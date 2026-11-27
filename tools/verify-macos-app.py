#!/usr/bin/env python3
"""Checks, without a Mac, that a macOS app is exactly the one that was signed by its developer and accepted by Apple.

Usage: python3 -I tools/verify-macos-app.py <folder of the .app or ZIP holding it> [pins name]

What is checked (as codesign --verify does on a Mac):
  - every slice of the program (x86_64, arm64): its code directories have the CDHash pinned below (the fingerprint of the
    signature; for x86_64 the one Apple's check accepted on a Mac of a user), every page of the program has the hash its code
    directory says, and so do Info.plist and _CodeSignature/CodeResources
  - every file sealed in CodeResources has its SHA-256, no sealed file is missing, and no file is there that is not sealed
    (apart from the program, Info.plist, PkgInfo and the signature itself, which are covered otherwise)
  - if openssl is there: the CMS signature of each slice is valid for its code directory, lists the pinned CDHashes, and its
    certificate is the developer's (team ID), issued by Apple's Developer ID CA under the Apple Root CA (pinned by its SHA-256)

Exit code 0 only if everything fits. Nothing of the app is run."""
import hashlib, os, plistlib, shutil, struct, subprocess, sys, tempfile, zipfile

APPLE_ROOT_CA_SHA256 = 'b0b1730ecbc7ff4505142c49f1295e6eda6bcaed7e2c68c5be91b5a11001f024'

PINS = {
    # DOSBox SVN r4484 for macOS by Dominus (Dominik Reichardt), signed 4 April 2025, notarized; checked on a Mac by a user
    # (codesign -dv, spctl -a: accepted, Notarized Developer ID) and here by its CMS signature
    'dosbox-svn-r4484': {
        'executable': 'Contents/MacOS/dosbox',
        'identifier': 'com.dosbox.dosbox',
        'team': '5PRK96AWD5',
        'cdhashes': {
            'x86_64': ['ea16d28c68bedff049cddc003aab70d8c6ef0987', 'af379dc4071880f14099e9d2b248af59e494db5cafc8c7887acdf215967f3822'],
            'arm64': ['26224d478c97bcb621ab1cd5f7760258bd03c4e4', 'fdf661f0a77f1084ded5e42929146bea66c8455d522687daca5be2698fe8e8c9'],
        },
        'pkginfo': b'APPL????',
    },
}

CPU = {0x01000007: 'x86_64', 0x0100000C: 'arm64'}
CSMAGIC_EMBEDDED_SIGNATURE, CSMAGIC_CODEDIRECTORY, CSSLOT_SIGNATURESLOT = 0xFADE0CC0, 0xFADE0C02, 0x10000


class Bad(Exception):
    pass


def u32(b, o):
    return struct.unpack_from('>I', b, o)[0]


def slices(data):
    if u32(data, 0) == 0xCAFEBABE:
        out = []
        for i in range(u32(data, 4)):
            cpu, _, off, size, _ = struct.unpack_from('>iiIII', data, 8 + 20 * i)
            out.append((CPU.get(cpu & 0xFFFFFFFF, hex(cpu & 0xFFFFFFFF)), data[off:off + size]))
        return out
    return [(CPU.get(struct.unpack_from('<I', data, 4)[0], 'thin'), data)]


def signature(macho):
    if struct.unpack_from('<I', macho, 0)[0] != 0xFEEDFACF:
        raise Bad('not a 64-bit Mach-O program')
    o = 32
    for _ in range(struct.unpack_from('<I', macho, 16)[0]):
        cmd, size = struct.unpack_from('<II', macho, o)
        if cmd == 0x1D:  # LC_CODE_SIGNATURE
            off, sz = struct.unpack_from('<II', macho, o + 8)
            return off, macho[off:off + sz]
        o += size
    raise Bad('not signed')


def blobs(sig):
    if u32(sig, 0) != CSMAGIC_EMBEDDED_SIGNATURE:
        raise Bad('no embedded signature')
    result = []
    for i in range(u32(sig, 8)):
        typ, off = struct.unpack_from('>II', sig, 12 + 8 * i)
        result.append((typ, sig[off:off + u32(sig, off + 4)]))
    return result


def code_directory(cd, macho, codeoff, info_plist, code_resources):
    _, _, version, _, hash_off, ident_off, n_special, n_code, code_limit, hash_size, hash_type, _, page_bits = struct.unpack_from('>IIIIIIIIIBBBB', cd, 0)
    if version >= 0x20400 and struct.unpack_from('>Q', cd, 56)[0]:
        code_limit = struct.unpack_from('>Q', cd, 56)[0]
    if code_limit != codeoff:
        raise Bad('the signature does not cover the whole program')
    h = {1: hashlib.sha1, 2: hashlib.sha256}.get(hash_type)
    if h is None:
        raise Bad('unknown hash type %d' % hash_type)
    team = None
    if version >= 0x20200 and u32(cd, 48):
        team = cd[u32(cd, 48):cd.index(b'\0', u32(cd, 48))].decode()
    page = 1 << page_bits
    if n_code != (code_limit + page - 1) // page:
        raise Bad('wrong number of pages')
    for i in range(n_code):
        if h(macho[i * page:min((i + 1) * page, code_limit)]).digest()[:hash_size] != cd[hash_off + i * hash_size:hash_off + (i + 1) * hash_size]:
            raise Bad('page %d of the program was changed' % i)

    def slot(k):
        return cd[hash_off - k * hash_size:hash_off - (k - 1) * hash_size] if k <= n_special else None
    if slot(1) != h(info_plist).digest()[:hash_size]:
        raise Bad('Info.plist was changed')
    if slot(3) != h(code_resources).digest()[:hash_size]:
        raise Bad('_CodeSignature/CodeResources was changed')
    return cd[ident_off:cd.index(b'\0', ident_off)].decode(), team, hashlib.sha1(cd).hexdigest() if hash_type == 1 else hashlib.sha256(cd).hexdigest()


def check_cms(arch, cms, first_cd, cdhashes, team, work):
    openssl = shutil.which('openssl')
    if not openssl:
        return 'openssl missing: the CMS signature was not checked (the pinned CDHashes were)'
    d = os.path.join(work, arch)
    os.makedirs(d)
    open(os.path.join(d, 'cms.der'), 'wb').write(cms)
    open(os.path.join(d, 'cd.bin'), 'wb').write(first_cd)
    run = lambda *a: subprocess.run([openssl] + list(a), capture_output=True, text=True, cwd=d)
    r = run('cms', '-verify', '-noverify', '-inform', 'DER', '-in', 'cms.der', '-binary', '-content', 'cd.bin', '-out', os.devnull, '-signer', 'leaf.pem')
    if r.returncode != 0:
        raise Bad(arch + ': the CMS signature does not fit the code directory')
    r = run('pkcs7', '-inform', 'DER', '-in', 'cms.der', '-print_certs')
    certs = [c for c in r.stdout.split('-----END CERTIFICATE-----') if '-----BEGIN CERTIFICATE-----' in c]
    root = ca = None
    for i, c in enumerate(certs):
        p = os.path.join(d, 'c%d.pem' % i)
        open(p, 'w').write(c[c.index('-----BEGIN'):] + '-----END CERTIFICATE-----\n')
        fp = run('x509', '-in', p, '-noout', '-fingerprint', '-sha256').stdout.split('=', 1)[-1].strip().replace(':', '').lower()
        subject = run('x509', '-in', p, '-noout', '-subject').stdout
        if fp == APPLE_ROOT_CA_SHA256:
            root = p
        elif 'CN = Developer ID Certification Authority' in subject:
            ca = p
    if root is None or ca is None:
        raise Bad(arch + ': the certificates are not those of Apple')
    leaf = run('x509', '-in', 'leaf.pem', '-noout', '-subject').stdout
    if ('UID = ' + team) not in leaf or ('OU = ' + team) not in leaf:
        raise Bad(arch + ': not signed by the team ' + team)
    signed = run('x509', '-in', 'leaf.pem', '-noout', '-startdate').stdout.split('=', 1)[1].strip()
    when = subprocess.run(['date', '-d', signed, '+%s'], capture_output=True, text=True).stdout.strip()
    r = run('verify', '-ignore_critical', '-attime', str(int(when) + 86400), '-CAfile', root, '-untrusted', ca, 'leaf.pem')
    if r.returncode != 0 or ': OK' not in r.stdout:
        raise Bad(arch + ': the certificate does not lead to the Apple Root CA')
    i = cms.find(b'<?xml')
    j = cms.find(b'</plist>', i)
    signed_hashes = [x.hex() for x in plistlib.loads(cms[i:j + 8]).get('cdhashes', [])] if i >= 0 and j > 0 else []
    if [x[:40] for x in cdhashes] != signed_hashes:
        raise Bad(arch + ': the signed CDHashes are others')
    return None


def check_resources(contents, code_resources, pin):
    files2 = plistlib.loads(code_resources).get('files2', {})
    for name, v in files2.items():
        p = os.path.join(contents, name)
        if not isinstance(v, dict) or 'hash2' not in v:
            raise Bad(name + ': a sealed entry of an unexpected kind')
        if not os.path.isfile(p):
            if v.get('optional'):
                continue
            raise Bad(name + ' is missing')
        if hashlib.sha256(open(p, 'rb').read()).digest() != v['hash2']:
            raise Bad(name + ' was changed')
    covered = set(files2) | {'Info.plist', 'PkgInfo', '_CodeSignature/CodeResources', pin['executable'][len('Contents/'):]}
    for root, dirs, files in os.walk(contents):
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), contents).replace(os.sep, '/')
            if rel not in covered:
                raise Bad(rel + ' is not part of the signed app')
        for dd in dirs:
            if os.path.islink(os.path.join(root, dd)):
                raise Bad('a link in the app: ' + dd)
    pkg = os.path.join(contents, 'PkgInfo')
    if os.path.exists(pkg) and open(pkg, 'rb').read().strip() != pin['pkginfo']:
        raise Bad('PkgInfo is not the usual one')
    return len(files2)


def verify(app, pin_name='dosbox-svn-r4484'):
    """the app folder (…/X.app) checked against its pins; a list of notes (empty when all was checked); Bad when it does not fit"""
    pin = PINS[pin_name]
    contents = os.path.join(app, 'Contents')
    exe = open(os.path.join(app, pin['executable']), 'rb').read()
    info_plist = open(os.path.join(contents, 'Info.plist'), 'rb').read()
    code_resources = open(os.path.join(contents, '_CodeSignature', 'CodeResources'), 'rb').read()
    notes = []
    found = {}
    with tempfile.TemporaryDirectory() as work:
        for arch, macho in slices(exe):
            off, sig = signature(macho)
            cds, cms = [], None
            for typ, b in blobs(sig):
                if u32(b, 0) == CSMAGIC_CODEDIRECTORY:
                    ident, team, cdhash = code_directory(b, macho, off, info_plist, code_resources)
                    if ident != pin['identifier'] or team != pin['team']:
                        raise Bad(arch + ': identifier or team are others (%s, %s)' % (ident, team))
                    cds.append((b, cdhash))
                elif typ == CSSLOT_SIGNATURESLOT:
                    cms = b[8:]
            hashes = [h for _, h in cds]
            if hashes != pin['cdhashes'].get(arch):
                raise Bad(arch + ': the signature is another one (CDHash %s)' % ', '.join(hashes))
            if cms is None:
                raise Bad(arch + ': no CMS signature')
            note = check_cms(arch, cms, cds[0][0], hashes, pin['team'], work)
            if note:
                notes.append(note)
            found[arch] = hashes[-1]
    if set(found) != set(pin['cdhashes']):
        raise Bad('the program does not have the slices ' + ', '.join(sorted(pin['cdhashes'])))
    n = check_resources(contents, code_resources, pin)
    return notes, found, n


def app_folder(path, work):
    """the .app folder, from a folder or a ZIP (unpacked into work, no absolute paths, no .., no links)"""
    if os.path.isdir(path):
        return path
    with zipfile.ZipFile(path) as z:
        for i in z.infolist():
            parts = i.filename.replace('\\', '/').split('/')
            if i.filename.startswith(('/', '\\')) or '..' in parts or ':' in parts[0]:
                raise Bad('unsafe name in the ZIP: ' + i.filename)
            if ((i.external_attr >> 16) & 0o170000) == 0o120000:
                raise Bad('a link in the ZIP: ' + i.filename)
        z.extractall(work)
    apps = [d for d in os.listdir(work) if d.endswith('.app') and os.path.isdir(os.path.join(work, d))]
    if len(apps) != 1:
        raise Bad('the ZIP must hold exactly one .app folder')
    return os.path.join(work, apps[0])


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    with tempfile.TemporaryDirectory() as work:
        try:
            notes, found, n = verify(app_folder(sys.argv[1], work), sys.argv[2] if len(sys.argv) > 2 else 'dosbox-svn-r4484')
        except (Bad, OSError, ValueError, KeyError, struct.error) as e:
            print('NOT OK:', e)
            sys.exit(1)
    for arch, h in sorted(found.items()):
        print('%s: signature and every page ok (CDHash %s)' % (arch, h[:40]))
    print('%d sealed files ok, nothing added' % n)
    for note in notes:
        print('note:', note)
    print('OK')


if __name__ == '__main__':
    main()
