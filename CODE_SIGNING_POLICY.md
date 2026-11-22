# Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).

## What is signed

`launch.exe`, the Windows starter of DBGL Extended Edition, in the stand-alone packages for Windows and in the update package. It is built from [`src/launcher/launch4j.xml`](src/launcher/launch4j.xml) with [Launch4j](https://launch4j.sourceforge.net) on a GitHub-hosted runner by the workflow [`sign-launcher.yml`](.github/workflows/sign-launcher.yml), and signed only from there.

Not signed by this project, because they are not built by it: DOSBox 0.74-3 (bundled in the Windows and macOS packages, GPL 2, from [dosbox.com](https://www.dosbox.com)), the Eclipse Temurin Java runtime in the Windows package (from [Adoptium](https://adoptium.net), signed by its makers) and the Java libraries in `lib` (each under its own open source license, see `licenses`).

## Team and roles

DBGL Extended Edition is maintained by one person, who holds all roles:

- Committer and reviewer: [mrmercy82](https://github.com/mrmercy82)
- Approver (approves every signing request by hand): [mrmercy82](https://github.com/mrmercy82)

Contributions from others are reviewed by the committer before they are merged. All accounts used for the source repository and for signing use multi-factor authentication.

## Privacy policy

DBGL Extended Edition does not collect, store or send any personal data to the project or its maintainer, and contains no telemetry or tracking.

It contacts other services on the internet only for what the user asks for, and only with what that needs (e.g. the title of a game to look up):

- online information and pictures for profiles (MobyGames, IGDB, RAWG, ScreenScraper, TheGamesDB, Pouet, SteamGridDB), each with the user's own key where the service requires one,
- sharing DOSBox configurations (share.dbgl.org, the service of the original DBGL by Ronald Blankendaal),
- downloading DOSBox versions and updates of DBGL Extended Edition from GitHub, and Java from Adoptium (api.adoptium.net), when the user starts this.

One check runs by itself: once a day DBGL Extended Edition asks GitHub (api.github.com) for the latest releases of DOSBox Staging and DOSBox-X, and of ScummVM (its repository scummvm/scummvm there) when ScummVM is entered in DBGL Extended Edition. Nothing is downloaded, and nothing is sent but the request itself; a newer ScummVM is only reported, with a link to its download page (scummvm.org), which opens in the browser only when the user clicks it. The check can be switched off in the settings ("Look for new DOSBox versions once a day"). The check for new versions of DBGL Extended Edition itself is off until the user switches it on.

ScummVM: DBGL Extended Edition reads the configuration file of ScummVM (scummvm.ini) on the computer to import and start its games, and never sends it anywhere. The online information and pictures for ScummVM games come from the services listed above, on request, like those for DOS games.

DOSBox, DOSBox Staging, DOSBox-X and ScummVM are separate programs that DBGL Extended Edition starts. DBGL Extended Edition passes them only what is needed to start the game. Any network features of their own are theirs, under their own privacy policies.

The services named above have their own privacy policies, which apply when DBGL Extended Edition contacts them.
