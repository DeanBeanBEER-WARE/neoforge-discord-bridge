# Discord Rank Bridge

Discord Rank Bridge is a NeoForge 1.21.1 modification designed to connect Minecraft servers with a Discord community. It synchronizes ranks, tracks player activity, manages punishments, and integrates natively with LuckPerms and Discord webhooks.

## Key Features

- **LuckPerms Integration:** Reads and assigns roles to players directly based on their Discord equivalent ranks.
- **Verification System:** Forces users to link their Minecraft accounts with Discord before allowing complete access to the server.
- **Maintenance Mode:** Prevents non-staff members from joining the server during active maintenance windows.
- **Mute Service:** Synchronizes mutes across Minecraft and Discord, ensuring a consistent moderation environment.
- **AFK Tracking:** Accurately monitors player activity and handles inactivity gracefully without affecting accurate playtime statistics.
- **Player Statistics:** Tracks and stores playtime and session metadata, integrating tightly with TAB to display real-time information.
- **Webhook Server:** Dedicated built-in HTTP server listening for commands and state updates triggered directly from Discord bots.

## Prerequisites

- Minecraft 1.21.1
- NeoForge 21.1.x
- LuckPerms (NeoForge version)
- A connected Discord bot application configured to communicate with the integrated webhook server.

## Installation

1. Place the compiled `discordrankbridge.jar` into the `mods` folder of your NeoForge server.
2. Start the server once to generate the default configuration files.
3. Stop the server and navigate to the `config/discordrankbridge` directory.
4. Modify the `discordrankbridge-common.toml` and other generated JSON configuration files according to your server and Discord setup.

## Configuration

The configuration covers various modules, including:
- Webhook Server ports and authentication tokens.
- Discord status update URLs.
- Message localization and customization (kicks, chat messages, broadcast events).
- Verification enforcement settings.

## Building from Source

This project uses Gradle. To build the mod from the source code, run the following command in the project root:

```sh
./gradlew build
```

The resulting JAR file will be located in the `build/libs` directory.

## License

Please refer to the LICENSE file for more information regarding distribution and usage rights.
