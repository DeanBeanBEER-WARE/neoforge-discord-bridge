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

## Modules

The project is structured into several key modules handling specific aspects of the bridge:

- `DiscordRankBridge`: The main mod entry point handling initialization, events, and lifecycle.
- `Config` / `MessageConfig`: Handles TOML-based settings and JSON-based customized text formats.
- `WebhookServer` / `WebhookHandler`: Built-in HTTP server to receive and process JSON payloads from Discord.
- `ChatListener`: Intercepts in-game chat to forward it to Discord via webhooks.
- `LuckPermsService`: Manages player permission groups via LuckPerms API.
- `VerificationService` / `VerificationEnforcer`: Manages the Discord-Minecraft account linking process and restricts unverified players.
- `MaintenanceService`: Manages whitelist and server access during maintenance.
- `MuteService`: Handles local player mutes, tracking duration and expiry.
- `PlayerStatsService`: Tracks player statistics such as kills, deaths, and playtime.
- `AfkTracker`: Monitors player movement, rotation, and chat to determine AFK status.
- `TabPlaytimeIntegration`: Integrates with the TAB plugin to provide custom placeholders like `%drb_playtime%` and `%drb_afk%`.
- `CommandHandler`: Registers in-game commands like `/verify` and `/debugplaytime`.
- `Mixin` classes: `PlayerListMixin` and `PlayerPickupMixin` to modify vanilla behavior for AFK suppression and unverified item pickup prevention.

## File Structure

```text
DiscordRankBridge-MDK/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── discordrankbridge/
        │       ├── mixin/
        │       │   ├── PlayerListMixin.java
        │       │   └── PlayerPickupMixin.java
        │       ├── AfkTracker.java
        │       ├── ChatListener.java
        │       ├── CommandHandler.java
        │       ├── Config.java
        │       ├── DiscordRankBridge.java
        │       ├── LuckPermsService.java
        │       ├── MaintenanceService.java
        │       ├── MessageConfig.java
        │       ├── MuteService.java
        │       ├── PlayerStatsService.java
        │       ├── TabPlaytimeIntegration.java
        │       ├── VerificationEnforcer.java
        │       ├── VerificationService.java
        │       ├── WebhookHandler.java
        │       ├── WebhookPayload.java
        │       └── WebhookServer.java
        └── resources/
            ├── META-INF/
            │   └── neoforge.mods.toml
            └── discordrankbridge.mixins.json
```

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
