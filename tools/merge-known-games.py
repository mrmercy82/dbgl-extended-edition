#!/usr/bin/env python3
"""Merges contributed files of learned games into community/known-games.json.

Usage: python3 -I tools/merge-known-games.py contributed1.json [contributed2.json ...]

Checks each entry as DBGL does (a title, a start program with a valid name and a 32-digit MD5, at most 10 files, texts cut to
1000 characters) and adds those whose start program (name in any case + MD5) is not in the list yet. Entries already there stay
as they are. The list is written sorted by title. Nothing else of the contributed files is taken over."""
import json, os, re, sys

LIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'community', 'known-games.json')
MAX_TEXT = 1000
MAX_SIZE = 20 * 1024 * 1024


def text(o, key):
    v = o.get(key, '')
    return v.strip()[:MAX_TEXT] if isinstance(v, str) else ''


def valid_name(n):
    return bool(n.strip()) and len(n) <= 255 and '/' not in n and '\\' not in n and n not in ('.', '..')


def entries(path):
    if os.path.getsize(path) > MAX_SIZE:
        raise SystemExit(path + ': larger than 20 MB')
    with open(path, encoding='utf-8') as f:
        root = json.load(f)
    result = []
    for g in root.get('games', []) if isinstance(root, dict) else []:
        if not isinstance(g, dict) or not text(g, 'title'):
            continue
        files = []
        for kf in g.get('files', [])[:10] if isinstance(g.get('files'), list) else []:
            if not isinstance(kf, dict):
                continue
            name, md5 = text(kf, 'name'), text(kf, 'md5').lower()
            if valid_name(name) and re.fullmatch(r'[0-9a-f]{32}', md5):
                f = {'name': name, 'md5': md5}
                if kf.get('setup') is True:
                    f['setup'] = True
                files.append(f)
        if not any(not f.get('setup') for f in files):
            continue
        e = {'title': text(g, 'title')}
        for k in ('developer', 'publisher', 'year', 'genre'):
            if text(g, k):
                e[k] = text(g, k)
        e['files'] = files
        result.append(e)
    return result


def key(e):
    m = next(f for f in e['files'] if not f.get('setup'))
    return m['name'].upper() + '|' + m['md5']


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    with open(LIST, encoding='utf-8') as f:
        root = json.load(f)
    games = entries(LIST)
    keys = {key(e) for e in games}
    for path in sys.argv[1:]:
        added = 0
        for e in entries(path):
            if key(e) not in keys:
                keys.add(key(e))
                games.append(e)
                added += 1
        print('%s: %d added' % (path, added))
    games.sort(key=lambda e: (e['title'].lower(), key(e)))
    root['format'] = 1
    root['games'] = games
    with open(LIST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(root, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('%d games in %s' % (len(games), os.path.normpath(LIST)))


if __name__ == '__main__':
    main()
