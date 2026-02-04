# PitBot (Minecraft 1.8.9 Forge Mod) — PvP Automation Bot

A Forge mod for **Minecraft 1.8.9** that automates PvP behavior on PvP/Pit-like servers: target selection, smooth aiming, attack timing, movement patterns, and auto-heal with soup.  
Built as a practical project to explore **event-driven modding**, **game automation**, and **configuration-driven behavior**.

> ⚠️ **Disclaimer**: This project is for educational purposes (client modding, input simulation, state machines).  
> Using automation on public servers may violate server rules / ToS. Use responsibly and only where allowed.

---

## Features

- **Hotkey toggle**: enable/disable the bot with a single key
- **Targeting system**
  - selects targets from a configurable nickname list
  - filters invisible/dead/out-of-range players
  - chooses nearest valid target
- **PvP actions**
  - smooth aim rotations (yaw/pitch smoothing)
  - attack reach logic + configurable CPS (clicks per second)
  - randomized movement behavior (jump/strafe chances) to reduce “robotic” patterns
  - sprint reset logic and basic collision handling
- **Auto-heal “AutoSoup”**
  - uses soup when health drops below a threshold
  - inventory/hotbar scan for soup items
  - supports refilling logic and bowl dropping behavior
- **Session / runtime utilities**
  - periodic screenshot capture (for debugging / monitoring)
  - config file load/save (`pitbotconf`)
- **Simple GUI hook**
  - uses a lightweight GUI screen while bot is active
  - allows emergency stop via hotkey

---

## Tech Stack

- **Java 8**
- **Minecraft Forge 1.8.9** (ForgeGradle `2.1-SNAPSHOT`)
- Event bus (Forge): tick events, key input events, chat events
- Config via `java.util.Properties`

---


---

## How It Works (High-Level)

The bot is built around a simple **state machine**:

- **WALKING**: moves towards an area / tries to get into action
- **FIGHTING**: selects a valid target, rotates aim smoothly, attacks at configured CPS, randomizes strafe/jumps
- **FALLING**: handles edge cases like falling / repositioning

It listens to Forge events (ticks, key presses) and simulates player inputs using Minecraft key bindings.

---

## Controls

- **H** — Toggle bot ON/OFF  
  - When enabled, bot registers to Forge event bus
  - When disabled, bot unregisters and releases movement keys

---

## Configuration

On first run the bot creates a config file in the game directory:

- **File:** `pitbotconf`

Example parameters stored inside:

- `Aim_reach`
- `Attack_reach`
- `Aim_smoothness_(yaw,pitch)`
- `Attack_cps`
- `Jump_chance_(when_fighting)`
- `Strafe_chance_(when_fighting)`
- `Soup_health_threshold`
- `Target_List` (comma-separated nicknames)

You can edit `Target_List` to control who the bot is allowed to target.

---

## Build & Run (Development)

> Requirements: **JDK 8**, Gradle, Minecraft 1.8.9 Forge MDK environment

Typical ForgeGradle flow:

1. Import the project into IntelliJ IDEA (Gradle project)
2. Run ForgeGradle setup tasks (depending on your environment)
3. Use the generated run configuration (client) to launch a dev instance
4. In-game, press **H** to toggle

---

## What This Project Demonstrates (for CV)

- Working with a **legacy game modding stack** (Minecraft 1.8.9 + ForgeGradle)
- **Event-driven architecture** (Forge event bus)
- A practical **finite state machine** to drive behavior
- Input simulation via **KeyBinding**
- Configurable behavior via **properties file**
- Clean separation of concerns:
  - `Bot` (core logic), `AutoSoupMod` (healing), `Utils` (math/helpers)

---

## Notes / Limitations

- Designed for Minecraft **1.8.9** (legacy version by design)
- Some functionality is server-specific (e.g., lobby/spawn behavior)
- Automation may be considered unfair on many servers — use only where permitted

---

## License

This repository is shared as a portfolio/educational project.  
If you want a specific license (MIT/Apache-2.0), add `LICENSE` and I’ll format it.

