# craftlatro

> A Fabric mod for Minecraft 1.26.2 that lets a player run the real Balatro game inside Minecraft. The player supplies their own Balatro.exe.

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.26.2-blue)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric%201.26.2-black?logo=fabric)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=java)](https://adoptium.net/)

## Description

A Fabric mod for Minecraft 1.26.2 that lets a player run the real Balatro game inside Minecraft. The player supplies their own Balatro.exe; the mod extracts its `.love`, serves a love.js package to an in-game Chromium browser, and surfaces it on a flat in-game screen with Minecraft controls mapped to the game. No Balatro code or assets are bundled or distributed.

## Table of Contents

- [Security](#security)
- [Installation](#installation)
- [Usage](#usage)
- [Contributing](#contributing)
- [License](#license)

## Installation

**Prerequisites**: Minecraft Java Edition 1.26.2, [Fabric Loader](https://fabricmc.net/) (with Fabric API), and Java 21. Minecraft 1.26.x requires Java 21 (see PLAN.md target stack). Rinku v3.0.4 (Chromium 151) is fetched at runtime by the mod.

1. Install the [Fabric Loader installer](https://fabricmc.net/use/installer/) for 1.26.2 with Fabric API.
2. Download the latest `craftlatro-*.jar` from the [releases page](https://github.com/CoderMan73/craftlatro/releases).
3. Place the jar in your `mods/` folder and launch Minecraft 1.26.2 via the Fabric profile.
4. In-game, supply your Balatro.exe when prompted (see Usage) — the mod does not bundle Balatro.

## Usage

On first entering a world without a configured Balatro.exe, a chat message appears:

> [Craftlatro] Balatro won't work without the game. Click here to select balatro.exe.

- Click the chat link (or use the keybind / `/craftlatro select` command) to open a file picker for your `Balatro.exe`.
- The mod records the path and, on opening the game screen, extracts the `.love`, packages it with the love.js (2dengine) player, and serves it over a local HTTP server (with COOP/COEP headers) to the embedded Rinku Chromium browser.
- Open the flat in-game GUI screen to play. Minecraft keyboard/mouse input is mapped to the browser.

## Security

No SECURITY.md is published yet. Vulnerability reports are handled per-repo by the maintainers. Note the project's core restriction: never bundle Balatro code/assets or the love.js runtime in the mod jar (see [AGENTS.md](AGENTS.md) > Boundaries).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, coding conventions, and the pull request process.

## License

GNU General Public License v3.0 - see [LICENSE](LICENSE) for details.

## Credits

- Standard Readme spec - https://github.com/RichardLitt/standard-readme
- shields.io - https://shields.io/
