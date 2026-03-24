# Agent Context: Discord Rank Bridge Mod

## Project Overview
A NeoForge 1.21.1 mod that bridges a Minecraft server with a Discord bot. It synchronizes chat, manages player ranks via LuckPerms, and provides various server management utilities via a webhook API.

## Technical Architecture
- **Platform**: NeoForge 1.21.1 (Java 21).
- **Permissions**: LuckPerms API integration.
- **Network**: Built-in Java `HttpServer` listening on multiple configurable ports.
- **Logging**: Log4j Appender for intercepting `BetterForgeChat` messages.
- **Authentication**: Shared secret token in the `X-Auth-Token` header.
- **Mixins**: Mixin on `PlayerList.broadcastSystemMessage` to suppress NeoEssentials AFK broadcasts.
- **Persistence**: 
  - `verified_users.json`: Stores Discord-Minecraft account links.
  - `player_stats.json`: Stores persistent player statistics (Kills, Deaths, Playtime) for leaderboard generation.
  - `discordrankbridge-messages.json`: JSON-based message configuration for all customizable formats.
  - `muted_players.json`: Stores locally muted players with duration and reason.

## Build Instructions (CRITICAL)
**ALL DEVELOPMENT IS DONE IN THE MDK DIRECTORY**: `/Volumes/Privat/VSC/DiscordRankBridge-MDK`

**MANDATORY WORKFLOW**:
1.  **Edit Files**: Make all changes directly in the `../DiscordRankBridge-MDK/src` directory.
2.  **Build**: Execute the build command from the MDK directory.
3.  **Verify**: Always run a build after changes to ensure no compilation errors were introduced.

```bash
# Build Workflow
cd /Volumes/Privat/VSC/DiscordRankBridge-MDK
./gradlew clean build
```

The resulting JAR will be in `/Volumes/Privat/VSC/DiscordRankBridge-MDK/build/libs/`.

---

## Configuration Files

### TOML Config (`discordrankbridge-common.toml`)
NeoForge-managed config with sections:
- `[http]` - enabled, bindAddress, ports, path, secret
- `[rankMapping]` - Discord rank → LuckPerms group mappings
- `[chat]` - chatEnabled, webhookUrl, statusWebhookUrl, rankAliases
- `[afk]` - timeout (seconds, 0-3600, default 300)
- `[verification]` - enabled, title, subtitle, debuffReapplyInterval, titleInterval

### JSON Message Config (`discordrankbridge-messages.json`)
Auto-generated on first run in `config/discordrankbridge/`. All message formats are customizable without rebuilding:

```json
{
  "minecraft": {
    "discordChatBadge": "§1[§l§oᴅɪѕᴄᴏʀᴅ§r§1]",
    "discordChatFormat": "§f[{time}] | {badge}§7 {sender} : {message}",
    "broadcastBadge": "§e[§l§oꜱᴇʀᴠᴇʀ§r§e]",
    "broadcastFormat": "§f[{time}] | {badge} §l§c{message}",
    "afkTag": "§7[§l§oᴀꜰᴋ§r§7]",
    "maintenanceKickMessage": "§cServer is in maintenance mode.\n§7Only staff can join.",
    "defaultKickReason": "Kicked by administrator via Discord"
  },
  "discord": {
    "chatFormatBFC": "`[{timestamp}]` **[{rank}]** {message}",
    "chatFormatStandard": "`[{timestamp}]` {message}",
    "joinMessage": "`[{timestamp}]` ➡️ **{player}** joined the server",
    "leaveMessage": "`[{timestamp}]` ⬅️ **{player}** left the server",
    "serverOnline": "🟢 **Server is Online**",
    "serverOffline": "🔴 **Server is Offline**",
    "whitelistFormat": "**[Whitelist]** {message}",
    "avatarUrl": "https://minotar.net/avatar/{player}"
  },
  "afk": {
    "timeoutSeconds": 300,
    "movementThreshold": 0.1,
    "rotationThreshold": 5.0
  }
}
```

Placeholders: `{time}`, `{badge}`, `{sender}`, `{message}`, `{timestamp}`, `{rank}`, `{player}`

---

## Webhook Action API
The mod listens for POST requests with a JSON payload. All actions require a valid `X-Auth-Token`.

### 1. Chat Synchronization (`chat`)
Bridges Discord messages to Minecraft chat.
- **Payload**: `{ "action": "chat", "discordUsername": "string", "message": "string", "rank": "string (optional)", "minecraftName": "string (optional)" }`

### 2. Player Verification (`verify`)
Handles account linking via `/verify <code>`.
- **Payload**: `{ "action": "verify", "minecraftName": "string", "code": "string", "discordId": "string" }`

### 3. Player Unlink (`unlink`)
Removes Discord-Minecraft account link. Reapplies verification enforcement if player is online.
- **Payload**: `{ "action": "unlink", "minecraftName": "string", "discordId": "string" }`
- **Response**: `{ "success": boolean, "message": "string" }`

### 4. Get Verified User (`get_verified_user`)
Returns the verification status from `verified_users.json` for server-authoritative sync.
- **Payload**: `{ "action": "get_verified_user", "minecraftName": "string" }`
- **Response**: `{ "minecraftName": "string", "discordId": "string|null", "isVerified": boolean }`

### 5. Rank Management (`add` / `remove`)
Updates LuckPerms groups based on Discord roles.
- **Payload**: `{ "action": "add", "minecraftName": "string", "rank": "string" }`

### 4. Whitelist Management (`whitelist_add` / `whitelist_remove`)
- **Payload**: `{ "action": "whitelist_add", "minecraftName": "string" }`

### 5. Server Info (`get_online_players`)
- **Response**: `{ "onlineCount": int, "maxCount": int, "players": ["string"] }`

### 6. Performance Stats (`get_tps`)
- **Response**: `{ "tps": double, "mspt": double }`

### 7. Player Stats (`get_player_stats`)
- **Payload**: `{ "action": "get_player_stats", "minecraftName": "string" }`
- **Response**: `{ "minecraftName": "string", "health": double, "deaths": int, "kills": int, "playtime": double (hours) }`

### 8. Kick Player (`kick_player`)
- **Payload**: `{ "action": "kick_player", "minecraftName": "string", "reason": "string (optional)" }`

### 9. Broadcast Message (`broadcast_message`)
- **Payload**: `{ "action": "broadcast_message", "message": "string" }`

### 10. Server Uptime (`get_uptime`)
- **Response**: `{ "uptimeSeconds": long }`

### 11. Top Players Leaderboard (`get_top_players`)
- **Payload**: `{ "action": "get_top_players", "sortBy": "kda" | "playtime" }`
- **Response**: `{ "players": [{ "minecraftName": "string", "kda": double, "playtime": double }] }`

### 12. Stop Server (`stop_server`)
- **Response**: `{ "success": true, "message": "Server shutdown initiated." }`

### 13. Restart Server (`restart_server`)
- **Response**: `{ "success": true, "message": "Server restart initiated." }`

### 14. Execute Command (`execute_command`)
- **Payload**: `{ "action": "execute_command", "command": "say hello" }`
- **Response**: `{ "output": "Server: hello" }`

### 15. Set Maintenance Mode (`set_maintenance_mode`)
- **Payload**: `{ "action": "set_maintenance_mode", "mode": true }`

### 16. Update Maintenance Whitelist (`update_maintenance_whitelist`)
- **Payload**: `{ "action": "update_maintenance_whitelist", "allowedPlayers": ["Player1", "Player2"] }`

### 17. Mute Player (`mute_player`)
- **Payload**: `{ "action": "mute_player", "minecraftName": "string", "duration": int|null, "reason": "string" }`
- Duration in minutes. `null` = permanent.

### 18. Unmute Player (`unmute_player`)
- **Payload**: `{ "action": "unmute_player", "minecraftName": "string" }`

### 19. Get All Verified Users (`get_all_verified_users`)
Returns a list of all discord-minecraft linked accounts for full re-syncs.
- **Payload**: `{ "action": "get_all_verified_users" }`
- **Response**: `{ "count": int, "users": [{ "minecraftName": "string", "discordId": "string" }] }`

---

## Relevant Files

### Core
- `DiscordRankBridge.java`: Mod init, event listeners, uptime, maintenance login check. TAB init moved to `ServerStartedEvent`.
- `Config.java`: TOML config definitions incl. `[afk]` section.
- `MessageConfig.java`: JSON message config - singleton loaded on startup, helper format methods.

### Webhook
- `WebhookHandler.java`: Main action dispatcher for all webhook actions. Uses `MessageConfig.get()` for all formats.
- `WebhookServer.java`: HTTP server setup and context mapping.
- `WebhookPayload.java`: Data model (incl. `mode`, `allowedPlayers`, `command` fields).

### Chat
- `ChatListener.java`: Outgoing MC→Discord chat logic. Uses `MessageConfig.get()` for Discord message formatting.

### Services
- `LuckPermsService.java`: Permission logic.
- `VerificationService.java`: Account linking persistence. Links to `VerificationEnforcer` via setter.
- `PlayerStatsService.java`: Persistent player stats. Has `getFormattedPlaytime(ServerPlayer)` and `getFormattedPlaytime(UUID)` overloads.
- `MaintenanceService.java`: Maintenance mode state and whitelist.
- `MuteService.java`: Persistent mute state (duration/permanent) management and ticking expiry.
- `VerificationEnforcer.java`: Enforces verification requirements on unverified players.
  - Applies Blindness & Slowness Level 255 (infinite duration)
  - Re-applies debuffs every 5 seconds (configurable)
  - Shows title/subtitle every 30 seconds (configurable)
  - Blocks item pickup via `PlayerPickupMixin`
  - Auto-reapplies on respawn
  - Cleanup on successful verification

### AFK System
- `AfkTracker.java`: Tracks player activity (movement, rotation, chat, block interactions). Marks players AFK after configurable timeout. Uses `MessageConfig.get()` for thresholds and AFK tag.
- `mixin/PlayerListMixin.java`: Mixin on `PlayerList.broadcastSystemMessage` - cancels NeoEssentials AFK broadcast messages ("is now AFK" / "is no longer AFK").
- `mixin/PlayerPickupMixin.java`: Mixin on `ItemEntity.playerTouch` - prevents unverified players from picking up items.
- `discordrankbridge.mixins.json`: Mixin config file (includes both `PlayerListMixin` and `PlayerPickupMixin`).

### TAB Integration
- `TabPlaytimeIntegration.java`: TAB plugin integration. Registers `%drb_playtime%` and `%drb_afk%` placeholders.
  - `%drb_playtime%` → formatted playtime string
  - `%drb_afk%` → AFK tag from `AfkTracker.getAfkTag()`
  - Init called in `onServerStarted()` (after all mods loaded, TAB API available)
  - Gracefully handles missing TAB (logs info, no crash)
  - Refresh interval: 1000ms

### Commands
- `CommandHandler.java`: Static `/verify` and `/debugplaytime` registration. Registered in constructor via `RegisterCommandsEvent`.

---

## Formatting Rules
- **Badge Character**: Use small capitals for badges (e.g., `ꜱᴇʀᴠᴇʀ`, `ᴅɪѕᴄᴏʀᴅ`, `ᴀꜰᴋ`).
- **Formatting Codes**: Use `§` directly in Java strings.
- **Badge Style**: `[§l§oTEXT§r]` (Bold + Italic).
- **All formats are in `MessageConfig.java`** / `discordrankbridge-messages.json` - NOT hardcoded.

## Dependencies (build.gradle)
- `compileOnly "net.luckperms:api:5.4"` (Maven: repo.luckperms.net)
- `compileOnly "com.github.NEZNAMY:TAB-API:5.2.1"` (Maven: jitpack.io)
- `implementation "org.apache.commons:commons-lang3:3.14.0"`

## Known Issues / Notes
- **NeoForge Event Order**: `RegisterCommandsEvent` fires BEFORE `ServerStartingEvent`. Commands registered in constructor, services initialized in `ServerStartingEvent`, services accessed via getters at execution time.
- **PlayerInteractEvent is abstract**: Must register listeners for concrete subclasses (`RightClickBlock`, `LeftClickBlock`), NOT the abstract parent class.
- **NeoEssentials AFK Bug**: Even with `enabled: false` in NeoEssentials config, AFK broadcasts still fire. Our Mixin suppresses them.
- **TAB Init Timing**: TAB API not available during `ServerStartingEvent`. Must init in `ServerStartedEvent`.
- **BetterForgeChat Detection**: ChatListener uses `§` in messages as a fingerprint to prevent re-forwarding mod-generated messages to Discord.
