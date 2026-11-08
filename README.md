# DBGL Extended Edition

An unofficial, independent continuation of **DBGL – DOSBox Game Launcher 0.100 beta 17** by **Ronald Blankendaal** ([dbgl.org](https://dbgl.org), [announcement of 0.100 beta 17 on Vogons](https://www.vogons.org/viewtopic.php?p=1380082#p1380082)).

Ronald Blankendaal developed DBGL up to version 0.100 beta 17 and is **not involved** in this edition. The foundation and almost everything in it are his work of nearly twenty years: nearly three quarters of the program code are unchanged from his 0.100 beta 17 (of his roughly 42,000 lines, about 300 were changed or removed; the Extended Edition added about 17,000). The Extended Edition mainly adds support for current DOSBox versions (DOSBox Staging 0.83, DOSBox-X up to 2026) and tools for large profile collections. It was started by mrmercy82 with the help of AI.

*Deutsch:* Eine inoffizielle, unabhängige Weiterführung von DBGL 0.100 beta 17 von Ronald Blankendaal, der an dieser Ausgabe nicht beteiligt ist. Knapp drei Viertel des Programmcodes sind unverändert sein Werk. Sie ergänzt vor allem die Unterstützung aktueller DOSBox-Versionen. Siehe `src/resources/doc/whatsnew_de.txt`.

## Links

- **This edition:** https://github.com/mrmercy82/dbgl-extended-edition – source code, [downloads (releases)](https://github.com/mrmercy82/dbgl-extended-edition/releases), [reporting problems (issues)](https://github.com/mrmercy82/dbgl-extended-edition/issues)
- **The original DBGL by Ronald Blankendaal:** https://dbgl.org – official website; [announcement of 0.100 beta 17 on Vogons](https://www.vogons.org/viewtopic.php?p=1380082#p1380082)

## What's new

See [`src/resources/doc/whatsnew_en.txt`](src/resources/doc/whatsnew_en.txt) (also in DBGL under Help → New features) and the full [`CHANGELOG-en.txt`](CHANGELOG-en.txt) / [`CHANGELOG-de.txt`](CHANGELOG-de.txt).

## Installing

> [!WARNING]
> **This is an alpha. Back up your complete DBGL folder and all your DOSBox folders before installing** – copy the whole folder(s) somewhere else. If anything goes wrong, put the copy back and everything is as before.

- **Update an existing DBGL 0.100 beta 17 (or an earlier 0.101 alpha Extended Edition):** back up your DBGL folder and all your DOSBox folders first, then unzip `dbgl-<version>-update.zip` from the [releases](https://github.com/mrmercy82/dbgl-extended-edition/releases) over your DBGL folder. Profiles, database and settings are kept.
- **From source:** `ant` with the `build.xml` in this folder (Java 21 or later), or apply `dbgl-0100beta17-to-<version>.patch` to the original 0.100 beta 17 source.

Please compare the SHA-256 checksums of the downloads with those published next to them.

## License

GNU General Public License version 2 or (at your option) any later version, like the original – see `src/dist/shared/COPYING`.

- The copyright notices of the original author are kept in every source file.
- Every file changed since 0.100 beta 17 says so in its header ("Modified for the DBGL Extended Edition ... since <date>"); files added by the Extended Edition carry their own copyright line. What changed and when is listed in the changelogs, with a date for every version, and in the Git history.
- The FAT image reader in `src/java/org/dbgl/util/fat` comes from fat32-lib (JNode.org, Matthias Treydte) and is under the GNU LGPL 2.1 or later.

Bundled libraries keep their own licenses (Eclipse SWT and Nebula Gallery: EPL; HSQLDB: BSD-style; Apache Commons: Apache 2.0; jsoup: MIT; JSON-java: JSON license; Jersey: EPL 2.0 / GPL 2 with Classpath Exception). DOSBox 0.74-3 in `src/dist/winshared` is GPL 2.

## Feedback

Problems and test results (especially on Windows and macOS): please open an issue.
