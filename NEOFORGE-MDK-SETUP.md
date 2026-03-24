# NeoForge 1.21.1 MDK Setup Guide

Anleitung zum Erstellen eines neuen NeoForge 1.21.1 Mod-Projekts von Grund auf – basierend auf dem funktionierenden DiscordRankBridge-MDK Setup.

---

## Voraussetzungen

- **Java 21** (JDK, nicht JRE) – z.B. Eclipse Temurin oder Oracle JDK
- **Gradle** wird über den Gradle Wrapper mitgeliefert (kein manuelles Install nötig)
- **IDE**: IntelliJ IDEA oder VS Code empfohlen

```bash
# Java Version prüfen
java -version   # Muss 21+ sein
```

---

## 1. Verzeichnisstruktur erstellen

```bash
mkdir -p MeinMod-MDK
cd MeinMod-MDK

# Gradle Wrapper erzeugen (benötigt einmalig Gradle auf dem System)
gradle wrapper --gradle-version 8.10

# Oder: Gradle Wrapper Dateien manuell von einem bestehenden Projekt kopieren:
# - gradlew
# - gradlew.bat
# - gradle/wrapper/gradle-wrapper.jar
# - gradle/wrapper/gradle-wrapper.properties
```

### Finale Verzeichnisstruktur:
```
MeinMod-MDK/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    └── main/
        ├── java/
        │   └── com/example/meinmod/
        │       └── MeinMod.java
        └── resources/
            └── META-INF/
                └── neoforge.mods.toml
```

---

## 2. `settings.gradle`

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
```

---

## 3. `gradle.properties`

Alle Mod-spezifischen Werte an einer Stelle. Ersetze die Platzhalter:

```properties
# --- MOD INFO ---
mod_id=meinmod
mod_name=Mein Mod
mod_license=MIT
mod_version=1.0.0
mod_group_id=com.example
mod_authors=DeinName
mod_description=Beschreibung deines Mods.

# --- VERSIONEN ---
neo_version=21.1.219
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)
neo_version_range=[21.1,)
loader_version_range=[4,)

# --- MAPPINGS (optional, für besser lesbare Methodennamen) ---
parchment_mappings_version=2024.07.28
parchment_minecraft_version=1.21
```

---

## 4. `build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '2.0.30-beta'
}

version = project.mod_version
group = project.mod_group_id

base {
    archivesName = project.mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    // NeoForge Version – alle verfügbaren Versionen:
    // https://projects.neoforged.net/neoforged/neoforge
    version = project.neo_version

    runs {
        client {
            client()
        }
        server {
            server()
        }
    }

    mods {
        "${project.mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

repositories {
    mavenCentral()
    // Weitere Repos hier (z.B. für APIs):
    // maven { url = 'https://repo.luckperms.net/' }
    // maven { url = 'https://jitpack.io' }
}

dependencies {
    // Beispiele:
    // compileOnly "net.luckperms:api:5.4"
    // implementation "org.apache.commons:commons-lang3:3.14.0"
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

---

## 5. `src/main/resources/META-INF/neoforge.mods.toml`

Die zentrale Mod-Beschreibungsdatei für NeoForge:

```toml
modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "meinmod"
version = "1.0.0"
displayName = "Mein Mod"
description = '''
Beschreibung deines Mods.
'''

[[dependencies.meinmod]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.meinmod]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1,)"
ordering = "NONE"
side = "BOTH"

# Optional: Mixin-Config referenzieren
# [[mixins]]
# config = "meinmod.mixins.json"
```

> **WICHTIG**: `modId` muss exakt mit dem `@Mod("meinmod")` Wert in der Java-Hauptklasse übereinstimmen!

---

## 6. Minimale Mod-Hauptklasse

`src/main/java/com/example/meinmod/MeinMod.java`:

```java
package com.example.meinmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MeinMod.MODID)
public class MeinMod {
    public static final String MODID = "meinmod";
    private static final Logger LOGGER = LoggerFactory.getLogger(MeinMod.class);

    public MeinMod(IEventBus modEventBus, ModContainer modContainer) {
        // Event-Listener registrieren
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        LOGGER.info("[{}] Mod konstruiert!", MODID);
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[{}] Server startet!", MODID);
    }
}
```

---

## 7. Build & Run

```bash
# Kompilieren
./gradlew build

# JAR liegt danach in:
# build/libs/meinmod-1.0.0.jar

# Lokalen Server starten (zum Testen)
./gradlew runServer

# Lokalen Client starten (zum Testen)
./gradlew runClient
```

---

## 8. Optional: Mixin-Support

### 8a. Mixin-Config erstellen: `src/main/resources/meinmod.mixins.json`

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.meinmod.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "BeispielMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 8b. Mixin-Klasse: `src/main/java/com/example/meinmod/mixin/BeispielMixin.java`

```java
package com.example.meinmod.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class BeispielMixin {

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void meinmod$onBroadcast(Component message, boolean overlay, CallbackInfo ci) {
        // Beispiel: Bestimmte Nachrichten unterdrücken
        String text = message.getString();
        if (text.contains("unerwünschter Text")) {
            ci.cancel();
        }
    }
}
```

### 8c. In `neoforge.mods.toml` aktivieren:

```toml
[[mixins]]
config = "meinmod.mixins.json"
```

---

## 9. NeoForge Config (TOML)

```java
// In der Hauptklasse:
modContainer.registerConfig(ModConfig.Type.COMMON, MeineConfig.COMMON_SPEC);
```

NeoForge erstellt automatisch eine `.toml` Datei im `config/` Ordner.

---

## 10. Häufige Events

```java
// Server Events
NeoForge.EVENT_BUS.addListener(this::onServerStarting);    // ServerStartingEvent
NeoForge.EVENT_BUS.addListener(this::onServerStarted);      // ServerStartedEvent
NeoForge.EVENT_BUS.addListener(this::onServerStopping);     // ServerStoppingEvent

// Player Events
NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);         // PlayerEvent.PlayerLoggedInEvent
NeoForge.EVENT_BUS.addListener(this::onPlayerLeave);        // PlayerEvent.PlayerLoggedOutEvent

// Chat
NeoForge.EVENT_BUS.addListener(this::onChat);               // ServerChatEvent

// Commands (WICHTIG: Feuert VOR ServerStartingEvent!)
NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);   // RegisterCommandsEvent

// Server Tick
NeoForge.EVENT_BUS.addListener(this::onTick);               // ServerTickEvent.Post

// Block Interactions (NICHT abstract PlayerInteractEvent verwenden!)
NeoForge.EVENT_BUS.addListener(this::onRightClick);         // PlayerInteractEvent.RightClickBlock
NeoForge.EVENT_BUS.addListener(this::onLeftClick);          // PlayerInteractEvent.LeftClickBlock
```

> ⚠️ **ACHTUNG**: `PlayerInteractEvent` ist **abstrakt**! Immer die konkreten Subklassen registrieren (`RightClickBlock`, `LeftClickBlock`, `RightClickItem`, etc.), sonst `IllegalArgumentException` beim Start!

> ⚠️ **ACHTUNG**: `RegisterCommandsEvent` feuert **VOR** `ServerStartingEvent`. Commands müssen im Konstruktor registriert werden, nicht im `ServerStartingEvent`.

---

## 11. Deployment

Die fertige JAR aus `build/libs/` in den `mods/` Ordner des Minecraft-Servers kopieren. Der Server muss NeoForge 21.1.x installiert haben.

---

## Referenzen

- **NeoForge Docs**: https://docs.neoforged.net/
- **NeoForge Versions**: https://projects.neoforged.net/neoforged/neoforge
- **ModDev Plugin**: https://github.com/neoforged/ModDevGradle
- **Parchment Mappings**: https://parchmentmc.org/
