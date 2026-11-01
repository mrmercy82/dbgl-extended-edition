# DBGL Extended Edition

An unofficial, independent continuation of **DBGL – DOSBox Game Launcher 0.100 beta 17** by **Ronald Blankendaal** ([dbgl.org](https://dbgl.org)).

Ronald Blankendaal developed DBGL up to version 0.100 beta 17 and is **not involved** in this edition. The foundation and almost everything in it are his work of nearly twenty years. The Extended Edition mainly adds support for current DOSBox versions (DOSBox Staging 0.83, DOSBox-X up to 2026) and tools for large profile collections. It was started by mrmercy82 with the help of AI.

*Deutsch:* Eine inoffizielle, unabhängige Weiterführung von DBGL 0.100 beta 17 von Ronald Blankendaal, der an dieser Ausgabe nicht beteiligt ist. Sie ergänzt vor allem die Unterstützung aktueller DOSBox-Versionen. Siehe `src/resources/doc/whatsnew_de.txt`.

## What's new

See [`src/resources/doc/whatsnew_en.txt`](src/resources/doc/whatsnew_en.txt) (also in DBGL under Help → New features) and the full [`CHANGELOG-en.txt`](CHANGELOG-en.txt) / [`CHANGELOG-de.txt`](CHANGELOG-de.txt).

## Installing

- **Update an existing DBGL 0.100 beta 17:** copy your DBGL folder first, then unzip `dbgl-<version>-update.zip` over it. Profiles, database and settings are kept.
- **From source:** `ant` with the `build.xml` in this folder (Java 21 or later), or apply `dbgl-0100beta17-to-<version>.patch` to the original 0.100 beta 17 source.

Please compare the SHA-256 checksums of the downloads with those published next to them.

## License

GNU General Public License version 2 or (at your option) any later version, like the original – see `src/dist/shared/COPYING`.

- The copyright notices of the original author are kept in every source file.
- Every file changed since 0.100 beta 17 says so in its header ("Modified for the DBGL Extended Edition ... since <date>"); files added by the Extended Edition carry their own copyright line. What changed and when is listed in the changelogs, with a date for every version, and in the Git history.
- The FAT image reader in `src/java/org/dbgl/util/fat` comes from fat32-lib (JNode.org, Matthias Treydte) and is under the GNU LGPL 2.1 or later.

Bundled libraries keep their own licenses (Eclipse SWT and Nebula Gallery: EPL; HSQLDB: BSD-style; Apache Commons: Apache 2.0; jsoup: MIT; JSON-java: JSON license; Jersey: EPL 2.0 / GPL 2 with Classpath Exception). DOSBox 0.74-3 in `src/dist/winshared` is GPL 2.

## Feedback

Problems and test results (especially on Windows and macOS): please open an issue. `TESTEN-WINDOWS.txt` has a test checklist (German).
