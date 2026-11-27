#!/usr/bin/env python3
#  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
#  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
#
#  This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.
"""
Builds all packages of a release of DBGL Extended Edition from a commit of this repository (Java 21 JDK, git and Python 3):

  python3 tools/mkrelease.py [--jre OpenJDK21U-jre_x64_windows_hotspot_....zip] [--dosbox-svn-mac DOSBoxSVN.app.zip] [--out DIR] [commit]

  dbgl-<v>-update.zip                          launch.exe, lib, docs, plugins, db/dbconf.*, dbgl.jar, update-readme.txt
                                               (the one-click update finds it by this name, together with SHA256SUMS.txt)
  DBGL-Extended-Edition-<v>-update-source.zip  update/, source/ (patch against 0.100 beta 17, git archive), README.md,
                                               LIESMICH-README.txt, COPYING.txt
  DBGL-Extended-Edition-<v>-windows.zip        with the Java of --jre in the folder jre (left out without --jre)
  DBGL-Extended-Edition-<v>-windows-without-java.zip, -linux.tar.xz, -macos-x86.tar.gz, -macos-arm.tar.gz   (layout of build.xml)
  SHA256SUMS.txt

The texts update-readme.txt and LIESMICH-README.txt come from tools/release (@N@ = the number of the alpha).
With --dosbox-svn-mac (alpha 55) the macOS packages also hold DOSBox SVN r4484 by Dominus (universal, signed, notarized), as
DBGL.app/Contents/Resources/DOSBoxSVN.app unchanged, after tools/verify-macos-app.py found it to be exactly the signed app.
Since alpha 53 (rebuilt then and checked against the update packages of alpha 52).
"""
import argparse, glob, hashlib, io, os, shutil, subprocess, sys, tarfile, tempfile, time, zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTS = os.path.join(REPO, 'tools', 'release')
EMPTY_DIRS = ['captures', 'db', 'dosroot', 'export', 'profiles']
DOCS = {'CHANGELOG-de.txt': 'changelog-de.txt', 'CHANGELOG-en.txt': 'changelog-en.txt', 'NEU-IN-DER-EXTENDED-EDITION.txt': 'whats-new-de.txt',
        'NEW-IN-EXTENDED-EDITION.txt': 'whats-new-en.txt'}
TS = None


def run(*cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def git(*a, **kw):
    return subprocess.run(['git', '-C', REPO] + list(a), check=True, capture_output=True, **kw).stdout


def unix(b):
    return b.replace(b'\r\n', b'\n')


class Tree:
    """files of a package: path -> (bytes or source file, mode); kept in order"""

    def __init__(self):
        self.files, self.dirs, self.links = {}, [], {}

    def add(self, path, src=None, data=None, mode=0o644):
        self.files[path] = (data if data is not None else open(src, 'rb').read(), mode)

    def adddir(self, src, prefix, crlf_to_lf=False, skip=()):
        for root, ds, fs in os.walk(src):
            ds.sort()
            rel = os.path.relpath(root, src)
            for f in sorted(fs):
                p = os.path.normpath(os.path.join(prefix, rel, f)).replace(os.sep, '/')
                if any(p.endswith(x) for x in skip):
                    continue
                full = os.path.join(root, f)
                mode = 0o755 if os.access(full, os.X_OK) and not f.endswith(('.jar', '.dll', '.txt', '.conf')) else 0o644
                self.add(p, full, mode=mode)

    def zip(self, path, top=''):
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            done = set()
            if top:
                zi = zipfile.ZipInfo(top, TS); zi.external_attr = (0o40755 << 16) | 0x10; z.writestr(zi, b''); done.add(top)
            for p in sorted(set(self.dirs)):
                z.writestr(zipfile.ZipInfo(top + p.rstrip('/') + '/', TS), b'')
            for p, (data, mode) in self.files.items():
                parts = p.split('/')
                for i in range(1, len(parts)):
                    d = top + '/'.join(parts[:i]) + '/'
                    if d not in done and d.rstrip('/') + '/' not in [top + x.rstrip('/') + '/' for x in self.dirs]:
                        zi = zipfile.ZipInfo(d, TS); zi.external_attr = (0o40755 << 16) | 0x10; z.writestr(zi, b''); done.add(d)
                zi = zipfile.ZipInfo(top + p, TS); zi.compress_type = zipfile.ZIP_DEFLATED; zi.external_attr = (0o100000 | mode) << 16
                z.writestr(zi, data, compresslevel=9)

    def tar(self, path, mode, top=''):
        with tarfile.open(path, mode) as t:
            def info(name, typ, m, size=0):
                ti = tarfile.TarInfo(name); ti.type = typ; ti.mode = m; ti.size = size; ti.mtime = TS_EPOCH; ti.uname = ti.gname = ''; return ti
            seen = set()
            def parents(p):
                parts = p.split('/')
                for i in range(1, len(parts)):
                    d = top + '/'.join(parts[:i])
                    if d and d not in seen:
                        t.addfile(info(d, tarfile.DIRTYPE, 0o755)); seen.add(d)
            for d in sorted(self.dirs):
                parents(d + '/x')
            for p, (data, m) in self.files.items():
                parents(p)
                t.addfile(info(top + p, tarfile.REGTYPE, m, len(data)), io.BytesIO(data))
            for p, target in self.links.items():
                parents(p)
                ti = info(top + p, tarfile.SYMTYPE, 0o755); ti.linkname = target; t.addfile(ti)


def compile_tree(T, out):
    """the classes of the tree as ant's target jar has them: all sources for Java 21, Start for Java 8 (it tells an old Java what is needed)"""
    cp = ':'.join(glob.glob(T + '/src/dist/shared/lib/*.jar') + glob.glob(T + '/src/dist/linuxshared/lib/*.jar') + glob.glob(T + '/libtest/*.jar'))
    sources = [os.path.join(r, f) for r, _, fs in os.walk(T + '/src/java') for f in fs if f.endswith('.java')]
    open(out + '.txt', 'w').write('\n'.join(sources))
    env = {k: v for k, v in os.environ.items() if k != 'JAVA_TOOL_OPTIONS'}
    run('javac', '-nowarn', '-encoding', 'UTF-8', '--release', '21', '-d', out, '-cp', cp, '@' + out + '.txt', env=env, stdout=subprocess.DEVNULL)
    run('javac', '-nowarn', '-encoding', 'UTF-8', '--release', '8', '-d', out + '-start', '-cp', out + ':' + cp, T + '/src/java/org/dbgl/gui/Start.java', env=env,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.copytree(out + '-start', out, dirs_exist_ok=True)
    shutil.copytree(T + '/src/resources', out, dirs_exist_ok=True)


SVN_MAC_README = """DOSBox SVN r4484 for macOS (universal: Intel and Apple Silicon), built, signed and notarized by Dominus (Dominik
Reichardt, Developer ID 5PRK96AWD5) on 4 April 2025. It is here unchanged; DBGL Extended Edition checked its signature when
the package was made (tools/verify-macos-app.py). Thanks to Dominus.

DOSBox is free software under the GNU General Public License, version 2 (Contents/Documents/COPYING in DOSBoxSVN.app).
Its source: https://sourceforge.net/p/dosbox/code-0/4484/tree/dosbox/trunk/ (revision 4484); the libraries in
Contents/Resources/lib_* (SDL, SDL_net, SDL_sound, libpng, zlib, Ogg, Vorbis, FLAC) are under their own free licenses.
"""


def dosbox_svn_mac(path):
    """the files of DOSBoxSVN.app (path -> (bytes, mode)), only if it is exactly the app Dominus signed"""
    import importlib.util
    spec = importlib.util.spec_from_file_location('verify_macos_app', os.path.join(REPO, 'tools', 'verify-macos-app.py'))
    v = importlib.util.module_from_spec(spec); spec.loader.exec_module(v)
    work = tempfile.mkdtemp(prefix='dbgl-svnmac-')
    try:
        app = v.app_folder(path, work)
        try:
            notes, found, n = v.verify(app)
        except (v.Bad, OSError, ValueError, KeyError) as e:
            sys.exit('--dosbox-svn-mac: NOT the signed DOSBox SVN r4484: %s' % e)
        for note in notes:
            print('--dosbox-svn-mac:', note)
        print('--dosbox-svn-mac: DOSBox SVN r4484 checked (%s; %d sealed files)' % (', '.join(sorted(found)), n))
        files = {}
        for root, ds, fs in os.walk(app):
            ds.sort()
            for f in sorted(fs):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, app).replace(os.sep, '/')
                files[rel] = (open(full, 'rb').read(), 0o755 if rel == 'Contents/MacOS/dosbox' else 0o644)
        return files
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main():
    global TS, TS_EPOCH, W, OUT
    ap = argparse.ArgumentParser(description='all packages of a release of DBGL Extended Edition')
    ap.add_argument('commit', nargs='?', default='HEAD')
    ap.add_argument('--jre', help='the Java 21 JRE for Windows as Eclipse Temurin publishes it (zip); without it there is no -windows.zip')
    ap.add_argument('--dosbox-svn-mac', help='DOSBoxSVN.app (folder or ZIP) for the macOS packages; checked against its signature first')
    ap.add_argument('--out', default=os.path.join(REPO, 'dist-release'), help='folder for the packages (emptied first)')
    a = ap.parse_args()
    svn_mac = None
    if a.dosbox_svn_mac:
        svn_mac = dosbox_svn_mac(a.dosbox_svn_mac)  # before anything is built: a wrong file stops it at once
    W, OUT = tempfile.mkdtemp(prefix='dbgl-release-'), os.path.abspath(a.out)
    shutil.rmtree(OUT, ignore_errors=True); os.makedirs(W + '/tree'); os.makedirs(OUT)
    commit = git('rev-parse', a.commit).decode().strip()
    base = git('rev-list', '--max-parents=0', commit).decode().split()[-1]  # the first commit: DBGL 0.100 beta 17 as published
    TS_EPOCH = int(git('show', '-s', '--format=%ct', commit).decode())
    TS = time.localtime(TS_EPOCH)[:6]
    arch = W + '/src.zip'
    with open(arch, 'wb') as f:
        f.write(git('archive', '--format=zip', commit))
    with zipfile.ZipFile(arch) as z:
        z.extractall(W + '/tree')
    T = W + '/tree'
    props = dict(l.split('=', 1) for l in open(T + '/src/resources/version.properties').read().split('\n') if '=' in l)
    v = props['majorversion'].strip() + '.' + props['minorversion'].strip()          # 0.101alpha53
    vf = v.replace('.', '')                                                             # 0101alpha53
    num = props['minorversion'].strip().split('alpha')[1]
    print('version', v, 'commit', commit[:7])

    # 1. dbgl.jar as ant would make it (resources, compacted i18n, the manifest of alpha 52)
    c = W + '/classes'
    compile_tree(T, c)
    libs = sorted(os.listdir(T + '/src/dist/shared/lib'))
    cp = ':'.join([c] + [T + '/src/dist/shared/lib/' + l for l in libs])
    jc = W + '/jarc'; shutil.copytree(c, jc)
    for d in ('org/dbgl/test', 'org/dbgl/preprocess', 'org/dbgl/app'):
        shutil.rmtree(jc + '/' + d, ignore_errors=True)
    run('java', '-cp', cp, 'org.dbgl.preprocess.CompactResourceBundles', jc + '/i18n', stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    manifest = ('Main-Class: org.dbgl.gui.Start\nEnable-Native-Access: ALL-UNNAMED\nClass-Path: ' + ' '.join(['lib/' + l for l in libs]
                + ['lib/swtwin64.jar', 'lib/swtlin64.jar', 'lib/swtmac64.jar', 'lib/swtmac64.jar', './']) + '\n')
    open(W + '/m.txt', 'w').write(manifest)
    jar = W + '/dbgl.jar'
    run('jar', 'cfm', jar, W + '/m.txt', '-C', jc, '.')
    jarbytes = open(jar, 'rb').read()

    docs = {('docs/' + n): open(T + '/' + o, 'rb').read() for o, n in DOCS.items()}

    # 2. update zip
    up = Tree()
    up.add('launch.exe', T + '/src/dist/winshared/launch.exe')
    up.dirs.append('lib')
    for l in libs:
        up.add('lib/' + l, T + '/src/dist/shared/lib/' + l)
    up.add('lib/swtwin64.jar', T + '/src/dist/winshared/lib/swtwin64.jar')
    up.add('lib/swtlin64.jar', T + '/src/dist/linuxshared/lib/swtlin64.jar')
    for p in sorted(docs):
        up.add(p, data=docs[p])
    up.add('plugins/version.properties', T + '/src/resources/version.properties')
    for f in sorted(os.listdir(T + '/src/resources/i18n')):
        up.add('plugins/i18n/' + f, T + '/src/resources/i18n/' + f)
    for f in ('dbconf.data', 'dbconf.properties', 'dbconf.script'):
        up.add('db/' + f, T + '/src/dist/shared/db/' + f)
    up.add('dbgl.jar', data=jarbytes)
    readme = open(TEXTS + '/update-readme.txt').read().replace('@N@', num).encode()
    up.add('update-readme.txt', data=readme)
    upd = OUT + '/dbgl-%s-update.zip' % v
    up.zip(upd)

    # 3. update-source zip
    top = 'DBGL-Extended-Edition-%s-update-source/' % v
    so = Tree()
    so.add('source/dbgl-0100beta17-to-%s.patch' % vf, data=git('diff', '--binary', base, commit))
    so.add('source/src%s.zip' % vf, data=open(arch, 'rb').read())
    so.add('README.md', T + '/README.md')
    for p, (d, m) in up.files.items():
        so.files['update/' + p] = (d, m)
    so.dirs += ['update/lib']
    so.add('update/COPYING.txt', T + '/src/dist/shared/COPYING')
    so.add('LIESMICH-README.txt', data=open(TEXTS + '/LIESMICH-README.txt').read().replace('@N@', num).encode())
    so.add('COPYING.txt', T + '/src/dist/shared/COPYING')
    so.zip(OUT + '/DBGL-Extended-Edition-%s-update-source.zip' % v, top)

    name = 'DBGL-Extended-Edition-%s' % v

    def shared(tr, prefix='', lf=False):
        tr.adddir(T + '/src/dist/shared', prefix)
        if lf:
            for p in list(tr.files):
                if p.startswith(prefix + 'templates/') or p.startswith(prefix + 'xsl/') or p == prefix + 'COPYING':
                    d, m = tr.files[p]; tr.files[p] = (unix(d), m)
        tr.add(prefix + 'dbgl.jar', data=jarbytes)
        for p in sorted(docs):
            tr.add(prefix + p, data=docs[p])
        for d in EMPTY_DIRS:
            if not any(x.startswith(prefix + d + '/') for x in tr.files):
                tr.dirs.append(prefix + d)

    # 4. Windows, with and without Java
    win = Tree(); shared(win); win.adddir(T + '/src/dist/winshared', '')
    win.zip(OUT + '/%s-windows-without-java.zip' % name)
    if not a.jre:
        print('no --jre: no %s-windows.zip' % name)
    else:
      with zipfile.ZipFile(a.jre) as z:
        for i in z.infolist():
            if i.is_dir():
                continue
            rel = i.filename.split('/', 1)[1]  # jdk-21.0.x+y-jre/... -> jre/...
            win.add('jre/' + rel, data=z.read(i))
      win.zip(OUT + '/%s-windows.zip' % name)

    # 5. Linux
    lin = Tree(); shared(lin, lf=True); lin.adddir(T + '/src/dist/linuxshared', '')
    d, m = lin.files['dbgl']; lin.files['dbgl'] = (unix(d), 0o755)
    lin.tar(OUT + '/%s-linux.tar.xz' % name, 'w:xz')

    # 6. macOS
    for a in ('x86', 'arm'):
        mac = Tree(); mo = 'DBGL.app/Contents/MacOS/'
        mac.adddir(T + '/src/dist/macshared', '')
        mac.adddir(T + '/src/dist/mac_' + a, '')
        shared(mac, mo, lf=True)
        d, m = mac.files[mo + 'dbgl']; mac.files[mo + 'dbgl'] = (unix(d), 0o755)
        p = 'DBGL.app/Contents/Resources/DOSBox.app/Contents/MacOS/DOSBox'; d, m = mac.files[p]; mac.files[p] = (d, 0o755)
        d, m = mac.files['DBGL.app/Contents/Info.plist']; mac.files['DBGL.app/Contents/Info.plist'] = (d.replace(b'@@version@@', v.encode()), m)
        mac.links[mo + 'DOSBox-0.74-3'] = '../Resources/DOSBox.app/Contents/MacOS'
        if svn_mac:
            for rel, (data, m) in svn_mac.items():
                mac.files['DBGL.app/Contents/Resources/DOSBoxSVN.app/' + rel] = (data, m)
            mac.add('DBGL.app/Contents/Resources/DOSBoxSVN-README.txt', data=SVN_MAC_README.encode())
            mac.links[mo + 'DOSBox-SVN'] = '../Resources/DOSBoxSVN.app/Contents/MacOS'
        mac.tar(OUT + '/%s-macos-%s.tar.gz' % (name, a), 'w:gz', 'dbgl%s/' % vf)

    # 7. sums
    with open(OUT + '/SHA256SUMS.txt', 'w') as f:
        for n in sorted(os.listdir(OUT)):
            if n != 'SHA256SUMS.txt':
                f.write(hashlib.sha256(open(OUT + '/' + n, 'rb').read()).hexdigest() + '  ' + n + '\n')
    print(open(OUT + '/SHA256SUMS.txt').read())
    shutil.rmtree(W, ignore_errors=True)


if __name__ == '__main__':
    main()
