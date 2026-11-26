# Community list of known games

`known-games.json` is a shared list of DOS games that DBGL Extended Edition can recognize by their files when you import games from a folder.

Each entry holds the title, developer, publisher, year and genre of a game and its start program (and setup program, if any) with name and MD5 checksum. It holds no paths and nothing about the computer it came from.

## Using the list

In DBGL Extended Edition, open **Profiles › Manage learned games** and click **Load community list**. DBGL downloads this file and keeps it next to your own learned games (`config/known-games-community.json`). The import from a folder uses it after your own learned games, so your own entries always win.

## Contributing

1. Make profiles for your games as usual. When a profile has a title and at least one other detail (developer, publisher or year), DBGL learns the game. You can also select profiles and use **Profiles › Learn selected profiles for the recognition**.
2. Open **Profiles › Manage learned games**, select the games you want to share (or none for all) and click **Contribute**.
3. DBGL saves the games as a file and opens a new issue on GitHub. Attach the file there.

The maintainer checks the entries and merges them with `tools/merge-known-games.py`.
