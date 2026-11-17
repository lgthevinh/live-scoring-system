# Live Scoring System

A web-based live scoring system for robotics competitions. It runs on a local network and shows real-time scores,
match status, and rankings to referees, scorekeepers, teams, and spectators.

## Table of Contents

- [Features](#features)
- [Download](#download)
- [Quick Start](#quick-start)
- [Build from Source](#build-from-source)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Contact](#contact)

## Features

- Real-time scoring from referee tablets over WebSocket
- Match control, timer, and live field displays
- Event setup: teams, accounts, and match schedule generation
- Rankings with event-specific scoring rules
- Runs fully offline on a LAN, with bundled JRE distributions for Windows, macOS, and Linux

## Download

Download the latest release for your event from the
[Releases page](https://github.com/lgthevinh/live-scoring-system/releases).

## Quick Start

1. Extract the downloaded zip.
2. Run `run.bat` (Windows) or `sh run.sh` (macOS/Linux).
3. Open `http://<host-ip>` in Google Chrome and log in as `local`.

See the [User Guide](docs/user-guide.md) for hardware requirements, network setup, and how to run an event.

## Build from Source

Requires JDK 17+.

```bash
./gradlew run              # Build and start the server
./gradlew packageWindows   # Build a distribution zip (also: packageMac, packageLinux)
```

## Documentation

- [User Guide](docs/user-guide.md): setting up and running the system at an event
- [Contributing Guide](.github/CONTRIBUTING.md): project structure, code style, and workflow

## Contributing

Contributions are welcome. Please read the [Contributing Guide](.github/CONTRIBUTING.md) before opening a pull request.

## Contact

- `everwellmax@gmail.com`: maintainer
- `hirovn9395@gmail.com`: maintainer
- `truongcongminhquan09@gmail.com`: FANROC event support
