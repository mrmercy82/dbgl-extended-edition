"""Builds PC-98 .HDI test images following the layout DOSBox-X reads (bios_disk.cpp HDIHDR, PartitionLoadIPL1,
drive_fat.cpp IPL1 start sector = cyl*sectors*heads + head*sectors + sector, FAT BPB with 1024 bytes/sector).
usage: make_hdi.py <out.hdi> <disk sector size> [--fat12] [--no-ipl1] [--name NAME] [--startsector N]"""
import struct, sys

out = sys.argv[1]
ss = int(sys.argv[2])
fat12 = '--fat12' in sys.argv
no_ipl1 = '--no-ipl1' in sys.argv
name = sys.argv[sys.argv.index('--name') + 1] if '--name' in sys.argv else 'MS-DOS'
start_sector = int(sys.argv[sys.argv.index('--startsector') + 1]) if '--startsector' in sys.argv else 0
SECTORS, HEADS = 33, 8
CYLS = 40 if fat12 else 300
HEADER = 4096
disk_bytes = CYLS * HEADS * SECTORS * ss

# partition: cylinder 1 (plus optional sector offset) .. last cylinder
part_phys_start = 1 * HEADS * SECTORS + start_sector
part_bytes = disk_bytes - part_phys_start * ss
BPS = 1024
spc = 4
reserved = 1
nfats = 2
rootent = 512
rootsec = rootent * 32 // BPS
totsec = part_bytes // BPS
# sectors per FAT (iterate)
fatsz = 1
while True:
    clusters = (totsec - reserved - nfats * fatsz - rootsec) // spc
    need = ((clusters + 2) * (12 if fat12 else 16) + 7) // 8
    if (need + BPS - 1) // BPS <= fatsz:
        break
    fatsz += 1
assert (clusters < 4085) == fat12, clusters

part = bytearray(reserved * BPS)
bs = bytearray(BPS)
bs[0:3] = b'\xeb\x3c\x90'
bs[3:11] = b'NEC 2.00'
struct.pack_into('<HBHBHHBHHHII', bs, 11, BPS, spc, reserved, nfats, rootent, totsec if totsec < 65536 else 0, 0xf8, fatsz, SECTORS, HEADS, 0, totsec if totsec >= 65536 else 0)
struct.pack_into('<BBBI11s8s', bs, 36, 0x80, 0, 0x29, 0x12345678, b'TESTHDI    ', b'FAT12   ' if fat12 else b'FAT16   ')
bs[510:512] = b'\x55\xaa'
part[0:BPS] = bs

fat = {0: 0xff8 if fat12 else 0xfff8, 1: 0xfff if fat12 else 0xffff}
EOC = 0xfff if fat12 else 0xffff
cluster_bytes = spc * BPS
data = {}
next_cluster = [2]

def alloc(content):
    n = max(1, (len(content) + cluster_bytes - 1) // cluster_bytes)
    first = next_cluster[0]
    for i in range(n):
        c = first + i
        fat[c] = c + 1 if i < n - 1 else EOC
        data[c] = content[i * cluster_bytes:(i + 1) * cluster_bytes]
    next_cluster[0] += n
    return first

def entry(name83, attr, cluster, size):
    return struct.pack('<11sB10xHHHI', name83, attr, 0, 0x5721, cluster, size)

def name83(n, e=''):
    return (n.ljust(8) + e.ljust(3)).encode('ascii')

exe = b'MZ' + bytes(510)
game_exe = alloc(exe)
setup_com = alloc(b'\xc3')
sub_exe = alloc(exe)
sub_dir_cluster = next_cluster[0] + 0  # allocated below
# GAME\DATA directory and GAME directory
data_dir = bytearray(cluster_bytes)
game_dir_cl = next_cluster[0]
fat[game_dir_cl] = EOC; next_cluster[0] += 1
data_dir_cl = next_cluster[0]
fat[data_dir_cl] = EOC; next_cluster[0] += 1
d = bytearray(cluster_bytes)
d[0:32] = entry(b'.          ', 0x10, data_dir_cl, 0)
d[32:64] = entry(b'..         ', 0x10, game_dir_cl, 0)
d[64:96] = entry(name83('INSTALL', 'EXE'), 0x20, sub_exe, len(exe))
data[data_dir_cl] = bytes(d)
g = bytearray(cluster_bytes)
g[0:32] = entry(b'.          ', 0x10, game_dir_cl, 0)
g[32:64] = entry(b'..         ', 0x10, 0, 0)
g[64:96] = entry(name83('GAME', 'EXE'), 0x20, game_exe, len(exe))
g[96:128] = entry(name83('SETUP', 'COM'), 0x20, setup_com, 1)
g[128:160] = entry(name83('DATA'), 0x10, data_dir_cl, 0)
data[game_dir_cl] = bytes(g)

root = bytearray(rootsec * BPS)
root[0:32] = entry(b'TESTHDI    ', 0x08, 0, 0)
root[32:64] = entry(name83('GAME'), 0x10, game_dir_cl, 0)
bat = b'@echo off\r\ncd game\r\ngame\r\n'
root[64:96] = entry(name83('AUTOEXEC', 'BAT'), 0x20, alloc(bat), len(bat))

fatbytes = bytearray(fatsz * BPS)
for c, v in fat.items():
    if fat12:
        o = c * 3 // 2
        cur = fatbytes[o] | (fatbytes[o + 1] << 8)
        cur = (cur & 0x000f) | (v << 4) if c & 1 else (cur & 0xf000) | v
        fatbytes[o] = cur & 0xff; fatbytes[o + 1] = (cur >> 8) & 0xff
    else:
        struct.pack_into('<H', fatbytes, c * 2, v)

data_offset = (reserved + nfats * fatsz + rootsec) * BPS
with open(out, 'wb') as f:
    f.truncate(HEADER + disk_bytes)
    f.write(struct.pack('<8I', 0, 0, HEADER, HEADER + disk_bytes, ss, SECTORS, HEADS, CYLS))
    # IPL (sector 0) and partition table (sector 1)
    ipl = bytearray(ss)
    ipl[0:4] = b'\xeb\x0a\x90\x90'
    if not no_ipl1:
        ipl[4:8] = b'IPL1'
    ipl[ss - 2:ss] = b'\x55\xaa'
    f.seek(HEADER); f.write(ipl)
    pt = bytearray(ss)
    pc = part_phys_start // (HEADS * SECTORS)
    rem = part_phys_start % (HEADS * SECTORS)
    struct.pack_into('<BBBBBBHBBHBBH16s', pt, 0, 0xa1, 0xa1, 0, 0, rem % SECTORS, rem // SECTORS, pc, rem % SECTORS, rem // SECTORS, pc, SECTORS - 1, HEADS - 1, CYLS - 1, name.encode().ljust(16))
    f.seek(HEADER + ss); f.write(pt)
    base = HEADER + (0 if no_ipl1 else part_phys_start * ss)
    f.seek(base); f.write(part)
    for i in range(nfats):
        f.seek(base + (reserved + i * fatsz) * BPS); f.write(fatbytes)
    f.seek(base + (reserved + nfats * fatsz) * BPS); f.write(root)
    for c, content in data.items():
        f.seek(base + data_offset + (c - 2) * cluster_bytes); f.write(content)
print(out, 'disk sector', ss, 'FAT12' if fat12 else 'FAT16', 'clusters', clusters, 'partition at byte', base)
