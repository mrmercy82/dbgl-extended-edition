DBGL Extended Edition
=====================

An unofficial, independent continuation of DBGL - DOSBox Game Launcher
0.100 beta 17 by Ronald Blankendaal (https://dbgl.org). Ronald Blankendaal
developed DBGL up to version 0.100 beta 17 and is not involved in this
edition. The foundation and a large part of it are his work, the result of
nearly 20 years of development. This edition was made with AI assistance.

This is NOT the official DBGL. Please report problems with it here, not to
the original author:
  https://github.com/mrmercy82/dbgl-extended-edition/issues


What you need
-------------
Java 21 or newer (64 bit), for example Eclipse Temurin:
  https://adoptium.net/temurin/releases/?version=21
With an older Java, DBGL says so at the start instead of opening.


How to start
------------
Windows:  unpack the archive into a folder of your own (not into "Program
          Files"), then start launch.exe (or dbgl.cmd). DOSBox 0.74-3 is
          included; other versions (DOSBox Staging, DOSBox-X) can be added
          in DBGL on the tab "DOSBox versions".
          launch.exe looks for Java 21 or newer in a folder "jre" in the
          DBGL folder, then in JAVA_HOME and in the PATH, then in the
          registry. dbgl.cmd uses the Java in "jre" or the one in the PATH.
          launch.exe is not signed: if SmartScreen says "Windows protected
          your PC", click "More info", then "Run anyway".
Linux:    unpack the archive, then start ./dbgl. DOSBox is not included:
          install it with your distribution and add it on the tab "DOSBox
          versions". DBGL keeps its data in your home folder.
macOS:    unpack the archive and open DBGL.app. DBGL keeps its data in
          your home folder. DBGL.app is not signed by Apple: if macOS says it
          "cannot be opened" or "is damaged", right-click DBGL.app > Open >
          Open, or run once in Terminal:
            xattr -dr com.apple.quarantine /path/to/DBGL.app

This is an alpha version. Keep backups of your games and of the DBGL
folder (File menu > Manage backups).


Already using DBGL 0.92 to 0.100 beta 17 or an earlier DBGL Extended
Edition 0.101 alpha?
-----------------------------------------------------------------------
You do not need this package: the update package (dbgl-<version>-update.zip
from the same download page, or Help > Check for updates of the Extended
Edition) keeps your profiles, database and settings. DBGL older than 0.92:
first update it to the official DBGL 0.92 (dbgl.org) and start it once;
that converts the database.


What is new, licenses
---------------------
docs/whats-new-en.txt and docs/changelog-en.txt (also in the Help menu).
DBGL is free software under the GNU General Public License, see COPYING.
The libraries that come with it have their own licenses: see the folder
"licenses".
Source code: https://github.com/mrmercy82/dbgl-extended-edition


Deutsch
=======

Eine inoffizielle, unabhaengige Weiterfuehrung von DBGL - DOSBox Game
Launcher 0.100 beta 17 von Ronald Blankendaal (https://dbgl.org). Ronald
Blankendaal hat DBGL bis Version 0.100 beta 17 entwickelt und ist an dieser
Ausgabe nicht beteiligt. Das Fundament und ein Grossteil daran sind sein
Werk, das auf fast 20 Jahre Entwicklungszeit zurueckgeht. Diese Ausgabe ist
mit KI-Unterstuetzung entstanden.

Dies ist NICHT das offizielle DBGL. Probleme bitte hier melden, nicht beim
urspruenglichen Autor:
  https://github.com/mrmercy82/dbgl-extended-edition/issues

Voraussetzung: Java 21 oder neuer (64 Bit), z. B. Eclipse Temurin:
  https://adoptium.net/temurin/releases/?version=21
Mit einem aelteren Java meldet DBGL das beim Start, statt sich zu oeffnen.

Starten:
Windows:  Archiv in einen eigenen Ordner entpacken (nicht unter
          "Programme"), dann launch.exe (oder dbgl.cmd) starten. DOSBox
          0.74-3 liegt bei; weitere Versionen (DOSBox Staging, DOSBox-X)
          lassen sich in DBGL auf dem Reiter "DOSBox-Versionen" hinzufuegen.
          launch.exe sucht Java 21 oder neuer zuerst in einem Ordner "jre"
          im DBGL-Ordner, dann ueber JAVA_HOME und den PATH, zuletzt in der
          Registry. dbgl.cmd nimmt das Java aus "jre" oder aus dem PATH.
          launch.exe ist nicht signiert: Meldet SmartScreen "Der Computer
          wurde durch Windows geschuetzt", auf "Weitere Informationen" und
          dann "Trotzdem ausfuehren" klicken.
Linux:    Archiv entpacken, dann ./dbgl starten. DOSBox liegt nicht bei:
          ueber die Distribution installieren und auf dem Reiter
          "DOSBox-Versionen" eintragen. DBGL legt seine Daten im
          Benutzerordner ab.
macOS:    Archiv entpacken und DBGL.app oeffnen. DBGL legt seine Daten im
          Benutzerordner ab. DBGL.app ist nicht von Apple signiert: Meldet
          macOS, es "kann nicht geoeffnet werden" oder "ist beschaedigt",
          mit Rechtsklick auf DBGL.app > Oeffnen > Oeffnen starten, oder
          einmal im Terminal:
            xattr -dr com.apple.quarantine /Pfad/zu/DBGL.app

Dies ist eine Alpha-Version. Bitte Sicherungen der Spiele und des
DBGL-Ordners behalten (Menue Datei > Sicherungen verwalten).

Wer schon DBGL 0.92 bis 0.100 beta 17 oder eine fruehere DBGL Extended
Edition 0.101 alpha benutzt, braucht dieses Paket nicht: Das Update-Paket
(dbgl-<Version>-update.zip von derselben Download-Seite oder Hilfe > Nach
Updates der Extended Edition suchen) behaelt Profile, Datenbank und
Einstellungen. DBGL aelter als 0.92: zuerst auf das offizielle DBGL 0.92
(dbgl.org) aktualisieren und einmal starten; das wandelt die Datenbank um.

Was neu ist: docs/whats-new-de.txt und docs/changelog-de.txt (auch im
Hilfe-Menue). DBGL ist freie Software unter der GNU General Public License,
siehe COPYING. Die mitgelieferten Bibliotheken haben eigene Lizenzen: siehe
Ordner "licenses".
