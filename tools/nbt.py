"""Minimal NBT reader/writer, enough for Minecraft structure templates.

No third-party dependency on purpose -- this has to run from a clean checkout. Big-endian,
gzip-wrapped, which is what the structure loader expects from `data/<ns>/structure/*.nbt`.
"""
import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """Wraps a value with an explicit tag id, for the cases the Python type cannot imply."""

    __slots__ = ("id", "value")

    def __init__(self, tag_id, value):
        self.id = tag_id
        self.value = value


def _infer(value):
    if isinstance(value, Tag):
        return value.id
    if isinstance(value, str):
        return STRING
    if isinstance(value, bool):
        return BYTE
    if isinstance(value, int):
        return INT
    if isinstance(value, float):
        return DOUBLE
    if isinstance(value, dict):
        return COMPOUND
    if isinstance(value, list):
        return LIST
    raise TypeError(f"cannot infer NBT type for {type(value)}")


def _unwrap(value):
    return value.value if isinstance(value, Tag) else value


class _Writer:
    def __init__(self):
        self.buf = bytearray()

    def raw(self, data):
        self.buf += data

    def string(self, s):
        encoded = s.encode("utf-8")
        self.buf += struct.pack(">H", len(encoded)) + encoded

    def payload(self, tag_id, value):
        value = _unwrap(value)
        if tag_id == BYTE:
            self.buf += struct.pack(">b", int(value))
        elif tag_id == SHORT:
            self.buf += struct.pack(">h", value)
        elif tag_id == INT:
            self.buf += struct.pack(">i", value)
        elif tag_id == LONG:
            self.buf += struct.pack(">q", value)
        elif tag_id == FLOAT:
            self.buf += struct.pack(">f", value)
        elif tag_id == DOUBLE:
            self.buf += struct.pack(">d", value)
        elif tag_id == BYTE_ARRAY:
            self.buf += struct.pack(">i", len(value)) + bytes((v & 0xFF) for v in value)
        elif tag_id == STRING:
            self.string(value)
        elif tag_id == INT_ARRAY:
            self.buf += struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
        elif tag_id == LONG_ARRAY:
            self.buf += struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
        elif tag_id == LIST:
            items = list(value)
            # An empty list still needs an element type; END is what vanilla writes.
            elem = _infer(items[0]) if items else END
            self.buf += struct.pack(">bi", elem, len(items))
            for item in items:
                self.payload(elem, item)
        elif tag_id == COMPOUND:
            for key, val in value.items():
                child = _infer(val)
                self.buf += struct.pack(">b", child)
                self.string(key)
                self.payload(child, val)
            self.buf += struct.pack(">b", END)
        else:
            raise TypeError(f"unsupported tag id {tag_id}")


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, n):
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        return self.take(self.unpack(">H")).decode("utf-8")

    def payload(self, tag_id):
        if tag_id == BYTE:
            return self.unpack(">b")
        if tag_id == SHORT:
            return self.unpack(">h")
        if tag_id == INT:
            return self.unpack(">i")
        if tag_id == LONG:
            return self.unpack(">q")
        if tag_id == FLOAT:
            return self.unpack(">f")
        if tag_id == DOUBLE:
            return self.unpack(">d")
        if tag_id == BYTE_ARRAY:
            return list(self.take(self.unpack(">i")))
        if tag_id == STRING:
            return self.string()
        if tag_id == INT_ARRAY:
            return [self.unpack(">i") for _ in range(self.unpack(">i"))]
        if tag_id == LONG_ARRAY:
            return [self.unpack(">q") for _ in range(self.unpack(">i"))]
        if tag_id == LIST:
            elem = self.unpack(">b")
            count = self.unpack(">i")
            return [] if elem == END else [self.payload(elem) for _ in range(count)]
        if tag_id == COMPOUND:
            out = {}
            while True:
                child = self.unpack(">b")
                if child == END:
                    return out
                # Name must be read before the payload. `out[self.string()] = self.payload(...)`
                # looks equivalent but evaluates the right-hand side first and desyncs the stream.
                name = self.string()
                out[name] = self.payload(child)
        raise TypeError(f"unsupported tag id {tag_id}")


def write(path, root, root_name=""):
    w = _Writer()
    w.raw(struct.pack(">b", COMPOUND))
    w.string(root_name)
    w.payload(COMPOUND, root)
    # mtime=0 so regenerating unchanged geometry produces a byte-identical file rather than a
    # spurious diff. gzip.open() does not accept mtime; GzipFile does.
    with open(path, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=raw, mtime=0) as fh:
            fh.write(bytes(w.buf))


def read(path):
    with gzip.open(path, "rb") as fh:
        data = fh.read()
    r = _Reader(data)
    assert r.unpack(">b") == COMPOUND, "root tag is not a compound"
    r.string()
    return r.payload(COMPOUND)


def read_bytes(data):
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    assert r.unpack(">b") == COMPOUND
    r.string()
    return r.payload(COMPOUND)
