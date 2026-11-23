# Code signing policy

> **Status:** DBGL Extended Edition is not code signed at present. An application for free code signing with SignPath Foundation was declined for now, as the project is still young; it may be made again later. Until then Windows SmartScreen may warn when `launch.exe` is started for the first time: click "More info", then "Run anyway".

This page says what would be signed and how, and what DBGL Extended Edition sends over the internet (privacy policy below), which applies already now.

## What would be signed

`launch.exe`, the Windows starter of DBGL Extended Edition, in the stand-alone packages for Windows and in the update package. It can be built from [`src/launcher/launch4j.xml`](src/launcher/launch4j.xml) with [Launch4j](https://launch4j.sourceforge.net) on a GitHub-hosted runner by the workflow [`sign-launcher.yml`](.github/workflows/sign-launcher.yml); once signing is set up, it would be signed only from there.

The releases so far contain an unsigned launch.exe, built from the same configuration on the maintainer's computer.

Not to be signed by this project, because they are not built by it: DOSBox 0.74-3 (bundled in the Windows and macOS packages, GPL 2, from [dosbox.com](https://www.dosbox.com)), the Eclipse Temurin Java runtime in the Windows package (from [Adoptium](https://adoptium.net), signed by its makers) and the Java libraries in `lib` (each under its own open source license, see `licenses`).

## Team and roles

DBGL Extended Edition is maintained by one person, who holds all roles:

- Committer and reviewer: [mrmercy82](https://github.com/mrmercy82)
- Approver (would approve every signing request by hand): [mrmercy82](https://github.com/mrmercy82)

Contributions from others are reviewed by the committer before they are merged. The account of the source repository uses multi-factor authentication.

## Privacy policy

DBGL Extended Edition does not collect, store or send any personal data to the project or its maintainer, and contains no telemetry or tracking.

It contacts other services on the internet only for what the user asks for, and only with what that needs (e.g. the title of a game to look up):

- online information and pictures for profiles (MobyGames, IGDB, RAWG, ScreenScraper, TheGamesDB, Pouet, SteamGridDB), each with the user's own key where the service requires one,
- downloading DOSBox versions and updates of DBGL Extended Edition from GitHub, and Java from Adoptium (api.adoptium.net), when the user starts this.

One check runs by itself: once a day DBGL Extended Edition asks GitHub (api.github.com) for the latest releases of DOSBox Staging and DOSBox-X, and of ScummVM (its repository scummvm/scummvm there) when ScummVM is entered in DBGL Extended Edition. Nothing is downloaded, and nothing is sent but the request itself; a newer ScummVM is only reported, with a link to its download page (scummvm.org), which opens in the browser only when the user clicks it. The check can be switched off in the settings ("Look for new DOSBox versions once a day"). The check for new versions of DBGL Extended Edition itself is off until the user switches it on.

The shared DOSBox configurations of DBConf come from a database that is part of the download (made by Ronald Blankendaal for DBGL 0.100 beta 18); DBGL Extended Edition no longer contacts share.dbgl.org, and sharing an own configuration is no longer offered.

ScummVM: DBGL Extended Edition reads the configuration file of ScummVM (scummvm.ini) on the computer to import and start its games, and never sends it anywhere. The online information and pictures for ScummVM games come from the services listed above, on request, like those for DOS games.

DOSBox, DOSBox Staging, DOSBox-X and ScummVM are separate programs that DBGL Extended Edition starts. DBGL Extended Edition passes them only what is needed to start the game. Any network features of their own are theirs, under their own privacy policies.

The services named above have their own privacy policies, which apply when DBGL Extended Edition contacts them.
