# Live Scoring System

A web-based live scoring system for robotics competitions. It runs on a local network and shows real-time scores,
match status, and rankings to referees, scorekeepers, teams, and spectators.

## Table of Contents

- [Download](#download)
- [Quick Start](#quick-start)
- [Build from Source](#build-from-source)
- [Documentation](#documentation)
- [Contributing](#contributing)

## Download

Download the latest release for your event from the
[Releases page](https://github.com/lgthevinh/live-scoring-system/releases).

## Quick Start

1. Install the downloaded package: `.exe` or `.msi` on Windows, `.deb` or `.rpm` on Linux.
2. Start **Scoring System** from the Start menu or the application menu.
3. Open `http://<host-ip>` in Google Chrome and log in as `local`.

See the [User Guide](docs/user-guide.md) for hardware requirements, network setup, and how to run an event.

## Build from Source

Requires JDK 17+ (packaging uses JDK 21, downloaded by Gradle if missing).

```bash
./gradlew run              # Build and start the server
./gradlew jpackage         # Build installers for the current OS (Windows: exe, msi; Linux: deb, rpm)
```

## Documentation

- [User Guide](docs/user-guide.md): setting up and running the system at an event
- [Contributing Guide](.github/CONTRIBUTING.md): project structure, code style, and workflow

## Contributing

Contributions are welcome. Please read the [Contributing Guide](.github/CONTRIBUTING.md) before opening a pull request.