# Fill in the Wall

A modern remake of Hypixel's Hole in the Wall minigame

Originally for [HooHacks 2024](https://devpost.com/software/hole-in-the-wall-rush)

# Video
[![Fill in the Wall Video](https://img.youtube.com/vi/ARJ5J_cZsdk/0.jpg)](https://www.youtube.com/watch?v=ARJ5J_cZsdk)

# Building
You'll need:
- A Spigot/Paper 1.21 server
- A MySQL Server (can be run without, but highly recommended)
- SpigotMC BuildTools (https://www.spigotmc.org/wiki/buildtools/)

Since this plugin uses some NMS, you'll need to use BuildTools to install remapped jars into your .m2 repository. If you don't do this, the plugin won't compile.

![image](https://github.com/user-attachments/assets/e377c175-10e3-4b2f-a92e-6d18a15e6366)

*At the time of writing this, this plugin is for 1.21.4!*

Clone this repository onto your machine and build the Maven project with `mvn package`. You can also open IntelliJ and do it from there.

![image](https://github.com/user-attachments/assets/2d49a7e4-8e6e-4fe7-bbd5-9aab6c9a2038)

Grab the jar file from the /target/ folder and place it in your Spigot/Paper server's plugin folder.

If you try to load the plugin in this state, the plugin will load, but it will say that it failed to connect to a SQL database and that personal bests will not persist and leaderboards will not work.

To fix this, in the config file that's generated at plugins/FillInTheWall/config.yml, configure the database settings. You'll need to manually create a database in the MySQL server you're connecting to, as the plugin will not do this for you. *May change in the future!*

# Config

- The `singleplayer-portal` and `multiplayer-portal` locations define where the big clickable display entities are located. *You'll figure it out.*
- The `multiplayer-spawn` location defines where players will be teleported back to after a multiplayer game ends, or when a player runs `/fitw spawn`.
- The `spectator-finals-spawn` location defines where eliminated players will spawn to spectate during the finals of a multiplayer game.
- The locations in the `leaderboards` section define where leaderboards will spawn.
- The `playingfields.yml` file holds the parameters of each playing field. To add a new playing field, run `/registerplayingfield` in-game and follow the instructions.

Playing fields with names that start with certain things will be included in multiplayer games:
- if name starts with "field_multi" it will be used in multiplayer qualification rounds
- if name starts with "field_finals" it will be used in multiplayer finals rounds

*This will also change in the future!*

# Custom Walls

Custom walls are grouped into "wall bundles". To add custom walls, create a new folder inside the /plugins/FillInTheWall/ folder called `custom`, and create a new YAML file inside of that.

There are two components to a wall bundle YAML file:
- The dimensions of each wall
  - Length
  - Height
- The list of walls
  - Wall name
    - Wall holes
    - Time override (in ticks)

The hole formatting are just numbers separated by commas. In a way, these are just ordered pairs, except without any parentheses around them.
For example, the wall with holes shaped like an Among Us character has the holes: `2,0,4,0,1,1,2,1,3,1,4,1,1,2,2,2,2,3,3,3,4,3`

An example of the wall bundle used in the finals on the server is in `resources/finals.yml` on this repository.

# Commands

- `/fitw reload`: Reloads the config. Can be unstable, so try not to use it.
- `/fitw abort`: Aborts any active multiplayer game.
- `/fitw timer`: Starts the pregame timer for multiplayer games.
- `/fitw start`: If a pregame timer is running, start the game immediately regardless of player count.
- `/fitw garbage`: Adds empty garbage walls to a player's playing field.
- `/fitw bundle`: Adds a wall bundle to a player's priority wall queue.
- `/fitw tip`: Displays an arbitrary tip on a player's playing field.
- `/fitw modifier`: Triggers a custom gimmick on a player's playing field.
- `/fitw custom`: Clears all queued walls and replaces them with the provided custom wall bundle
- `/fitw spawn`: Sends you to the location defined in `multiplayer-spawn` in the config file
