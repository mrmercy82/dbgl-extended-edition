"""Builds a small hard disk image with an MBR partition (FAT16 at sector 63) laid out like an installed Windows 95: MSDOS.SYS,
WINDOWS\\SYSTEM\\VMM32.VXD, WINDOWS\\WIN.INI and a Start menu with shortcuts (.LNK) under long names, for the tests of Win9x.
usage: make_win95_hdd.py <out.img>"""
import struct, sys

out = sys.argv[1]
SS = 512
SPC = 4
RESERVED = 1
NFATS = 2
ROOTENT = 512
ROOTSEC = ROOTENT * 32 // SS
START = 63
TOTAL = 20480  # 10 MB, more than 2.88 MB: read as a hard disk
PART = TOTAL - START
FATSZ = 1
while True:
    clusters = (PART - RESERVED - NFATS * FATSZ - ROOTSEC) // SPC
    if ((clusters + 2) * 2 + SS - 1) // SS <= FATSZ:
        break
    FATSZ += 1
assert 4085 <= clusters < 65525, clusters
CB = SPC * SS
EOC = 0xffff
fat = {0: 0xfff8, 1: 0xffff}
data = {}
nxt = [2]


def alloc(content):
    n = max(1, (len(content) + CB - 1) // CB)
    first = nxt[0]
    for i in range(n):
        fat[first + i] = first + i + 1 if i < n - 1 else EOC
        data[first + i] = content[i * CB:(i + 1) * CB]
    nxt[0] += n
    return first


def short(n, e=''):
    return (n.ljust(8) + e.ljust(3)).encode('ascii')


def entry(n83, attr, cluster, size):
    return struct.pack('<11sB10xHHHI', n83, attr, 0, 0x5721, cluster, size)


def lfn(longname, n83):
    s = 0
    for b in n83:
        s = (((s & 1) << 7) + (s >> 1) + b) & 0xff
    chars = [ord(c) for c in longname]
    if len(chars) % 13:
        chars += [0] + [0xffff] * (12 - len(chars) % 13)
    parts = [chars[i:i + 13] for i in range(0, len(chars), 13)]
    result = b''
    for i in range(len(parts), 0, -1):
        p = parts[i - 1]
        seq = i | (0x40 if i == len(parts) else 0)
        result += struct.pack('<B5HBBB6HH2H', seq, *p[0:5], 0x0f, 0, s, *p[5:11], 0, *p[11:13])
    return result


class Dir:
    def __init__(self, parent=None):
        self.entries = []
        self.parent = parent
        self.cluster = None

    def add(self, longname, n83, attr, cluster, size):
        e = b''
        if longname:
            e += lfn(longname, n83)
        self.entries.append(e + entry(n83, attr, cluster, size))

    def file(self, longname, n83, content):
        self.add(longname, n83, 0x20, alloc(content), len(content))

    def sub(self, longname, n83):
        d = Dir(self)
        d.cluster = nxt[0]
        fat[d.cluster] = EOC
        nxt[0] += 1
        self.add(longname, n83, 0x10, d.cluster, 0)
        return d

    def write(self):
        b = b''
        if self.parent is not None:
            b += entry(b'.          ', 0x10, self.cluster, 0)
            b += entry(b'..         ', 0x10, self.parent.cluster or 0, 0)
        b += b''.join(self.entries)
        return b


def link(target, name=None, unicode=False):
    """a shortcut as Windows 95 writes it: header, an item list, the link information with the local path, a name"""
    flags = 0x01 | 0x02 | (0x04 if name else 0) | (0x80 if unicode else 0)
    head = struct.pack('<I16sI', 0x4C, bytes(16), flags) + bytes(0x4C - 24)
    idlist = struct.pack('<H', 6) + struct.pack('<H', 4) + b'\x1f\x50' + struct.pack('<H', 0)
    path = target.encode('cp1252') + b'\0'
    info = struct.pack('<IIIIIII', 28 + len(path) + 1, 28, 1, 0, 28, 28 + len(path), 0) + path + b'\0'
    s = b''
    if name:
        s = struct.pack('<H', len(name)) + (name.encode('utf-16-le') if unicode else name.encode('cp1252'))
    return head + idlist + info + s


root = Dir()
root.file(None, short('MSDOS', 'SYS'), b'[Paths]\r\nWinDir=C:\\WINDOWS\r\nWinBootDir=C:\\WINDOWS\r\nHostWinBootDrv=C\r\n\r\n[Options]\r\nBootGUI=1\r\n')
win = root.sub(None, short('WINDOWS'))
system = win.sub(None, short('SYSTEM'))
system.file(None, short('VMM32', 'VXD'), b'MZ' + bytes(62))
win.file(None, short('WIN', 'INI'), b'[windows]\r\nload=\r\nrun=C:\\TOOLS\\CLOCK.EXE\r\n\r\n[Desktop]\r\nWallpaper=(None)\r\n')
win.file(None, short('NOTEPAD', 'EXE'), b'MZ' + bytes(62))
start = win.sub('Startmenü', short('STARTM~1'))
progs = start.sub('Programme', short('PROGRA~1'))
progs.file('EarthSiege 2.lnk', short('EARTHS~1', 'LNK'), link('C:\\SIERRA\\ES2\\ES2.EXE', 'EarthSiege 2'))
progs.file('Gone Game.lnk', short('GONEGA~1', 'LNK'), link('C:\\GAMES\\GONE.EXE'))
progs.file('CD Game.lnk', short('CDGAME~1', 'LNK'), link('D:\\SETUP.EXE', 'CD', unicode=True))
progs.file('Readme.lnk', short('README', 'LNK'), link('C:\\SIERRA\\ES2\\README.TXT'))
acc = progs.sub('Zubehör', short('ZUBEH~1'))
acc.file('Editor.lnk', short('EDITOR', 'LNK'), link('C:\\WINDOWS\\NOTEPAD.EXE'))
auto = progs.sub('Autostart', short('AUTOST~1'))
auto.file('Tool.lnk', short('TOOL', 'LNK'), link('C:\\SIERRA\\ES2\\ES2.EXE'))
sierra = root.sub(None, short('SIERRA'))
es2 = sierra.sub(None, short('ES2'))
es2.file(None, short('ES2', 'EXE'), b'MZ' + bytes(62))
es2.file(None, short('README', 'TXT'), b'readme\r\n')

for d in [win, system, start, progs, acc, auto, sierra, es2]:
    b = d.write()
    assert len(b) <= CB
    data[d.cluster] = b
rootbytes = root.write()
assert len(rootbytes) <= ROOTSEC * SS

bs = bytearray(SS)
bs[0:3] = b'\xeb\x3c\x90'
bs[3:11] = b'MSWIN4.1'
struct.pack_into('<HBHBHHBHHHII', bs, 11, SS, SPC, RESERVED, NFATS, ROOTENT, PART if PART < 65536 else 0, 0xf8, FATSZ, 63, 16, START, 0 if PART < 65536 else PART)
struct.pack_into('<BBBI11s8s', bs, 36, 0x80, 0, 0x29, 0x12345678, b'WIN95TEST  ', b'FAT16   ')
bs[510:512] = b'\x55\xaa'
fatbytes = bytearray(FATSZ * SS)
for c, v in fat.items():
    struct.pack_into('<H', fatbytes, c * 2, v)
mbr = bytearray(SS)
struct.pack_into('<B3sB3sII', mbr, 446, 0x80, b'\x01\x01\x00', 0x06, b'\xfe\x3f\xff', START, PART)
mbr[510:512] = b'\x55\xaa'
base = START * SS
dataoff = base + (RESERVED + NFATS * FATSZ + ROOTSEC) * SS
with open(out, 'wb') as f:
    f.truncate(TOTAL * SS)
    f.write(mbr)
    f.seek(base)
    f.write(bs)
    for i in range(NFATS):
        f.seek(base + (RESERVED + i * FATSZ) * SS)
        f.write(fatbytes)
    f.seek(base + (RESERVED + NFATS * FATSZ) * SS)
    f.write(rootbytes)
    for c, content in data.items():
        f.seek(dataoff + (c - 2) * CB)
        f.write(content)
print(out, 'FAT16 clusters', clusters)
