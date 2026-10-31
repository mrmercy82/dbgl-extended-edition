"""PC-98 test images besides .HDI: .NHD (converted from an .HDI made by make_hdi.py), .FDI and .D88 floppies (2HD, 1.23 MB:
77 cylinders, 2 heads, 8 sectors of 1024 bytes, FAT12), following the formats DOSBox-X reads.
usage: make_pc98_floppy.py nhd <in.hdi> <out.nhd> | fdi <out.fdi> | d88 <out.d88>"""
import struct, sys

def fat12_floppy():
    BPS, SPT, HEADS, CYLS = 1024, 8, 2, 77
    total = SPT * HEADS * CYLS
    reserved, nfats, rootent, fatsz, spc = 1, 2, 192, 2, 1
    rootsec = rootent * 32 // BPS
    img = bytearray(total * BPS)
    bs = bytearray(BPS)
    bs[0:3] = b'\xeb\x3c\x90'; bs[3:11] = b'NEC 2.00'
    struct.pack_into('<HBHBHHBHHHII', bs, 11, BPS, spc, reserved, nfats, rootent, total, 0xfe, fatsz, SPT, HEADS, 0, 0)
    bs[510:512] = b'\x55\xaa'
    img[0:BPS] = bs
    fat = {0: 0xffe, 1: 0xfff}
    def entry(n, attr, cl, size):
        return struct.pack('<11sB10xHHHI', n, attr, 0, 0x5721, cl, size)
    exe = b'MZ' + bytes(510)
    data_off = (reserved + nfats * fatsz + rootsec) * BPS
    def put(cl, content):
        img[data_off + (cl - 2) * BPS: data_off + (cl - 2) * BPS + len(content)] = content
        fat[cl] = 0xfff
    put(2, exe)                                   # GAME.EXE
    d = bytearray(BPS)                            # directory UTIL
    d[0:32] = entry(b'.          ', 0x10, 3, 0); d[32:64] = entry(b'..         ', 0x10, 0, 0)
    d[64:96] = entry(b'SETUP   COM', 0x20, 4, 1)
    put(3, bytes(d)); put(4, b'\xc3')
    root = bytearray(rootsec * BPS)
    root[0:32] = entry(b'GAME    EXE', 0x20, 2, len(exe))
    root[32:64] = entry(b'UTIL       ', 0x10, 3, 0)
    img[(reserved + nfats * fatsz) * BPS:(reserved + nfats * fatsz) * BPS + len(root)] = root
    fb = bytearray(fatsz * BPS)
    for c, v in fat.items():
        o = c * 3 // 2
        cur = fb[o] | (fb[o + 1] << 8)
        cur = (cur & 0x000f) | (v << 4) if c & 1 else (cur & 0xf000) | v
        fb[o] = cur & 0xff; fb[o + 1] = cur >> 8
    for i in range(nfats):
        o = (reserved + i * fatsz) * BPS
        img[o:o + len(fb)] = fb
    return bytes(img), BPS, SPT, HEADS, CYLS

mode = sys.argv[1]
if mode == 'nhd':
    hdi = open(sys.argv[2], 'rb').read()
    hs, size, ss, sectors, heads, cyls = struct.unpack_from('<IIIIII', hdi, 8)
    head = bytearray(0x200)
    head[0:15] = b'T98HDDIMAGE.R0\0'
    struct.pack_into('<IIHHH', head, 0x110, 0x200, cyls, heads, sectors, ss)
    open(sys.argv[3], 'wb').write(bytes(head) + hdi[hs:])
elif mode == 'fdi':
    img, ss, spt, heads, cyls = fat12_floppy()
    open(sys.argv[2], 'wb').write(struct.pack('<8I', 0, 0x90, 4096, 4096 + len(img), ss, spt, heads, cyls) + bytes(4096 - 32) + img)
elif mode == 'd88':
    img, ss, spt, heads, cyls = fat12_floppy()
    tracks = []
    for c in range(cyls):
        for h in range(heads):
            t = bytearray()
            for r in [3, 1, 4, 2, 5, 7, 6, 8]:    # stored out of order on purpose
                off = ((c * heads + h) * spt + (r - 1)) * ss
                t += struct.pack('<BBBBHBBB5xH', c, h, r, 3, spt, 0, 0, 0, ss) + img[off:off + ss]
            tracks.append(bytes(t))
    hdr = bytearray(0x2b0)
    hdr[0:8] = b'TESTD88\0'; hdr[0x1b] = 0x20      # 2HD
    pos = 0x2b0
    for i, t in enumerate(tracks):
        struct.pack_into('<I', hdr, 0x20 + i * 4, pos); pos += len(t)
    struct.pack_into('<I', hdr, 0x1c, pos)
    open(sys.argv[2], 'wb').write(bytes(hdr) + b''.join(tracks))
