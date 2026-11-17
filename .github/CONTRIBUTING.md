# Contributing

Thanks for helping improve the Live Scoring System. This guide covers the project layout, how to build it, and the
code style we follow.

## Table of Contents

- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Java Code Style](#java-code-style)
- [Frontend Code Style](#frontend-code-style)
- [Git Workflow](#git-workflow)

## Project Structure

```text
live-scoring-system/
├── src/
│   ├── main/java/
│   │   ├── org/thingai/app/        # Application code
│   │   │   ├── Main.java           # Entry point
│   │   │   ├── api/                # Javalin HTTP endpoints and WebSocket handlers
│   │   │   └── scoringservice/     # Core scoring logic, handlers, entities, repositories
│   │   └── eventimpl/              # Event-specific scoring and ranking rules (e.g. fanroc, demo)
│   ├── main/resources/             # Static web resources (built web UI is copied here)
│   ├── desktop/java/               # Swing desktop launcher (built into scoring-launcher.jar)
│   └── test/                       # Tests and test resources
├── webui/                          # Angular frontend
├── libs/                           # Internal jars (base-core, base-platform-jvm)
├── vendor/
│   ├── jre/                        # Bundled JREs per platform (linux, macos, windows)
│   └── binary/                     # MatchMaker executables for schedule generation
├── distribution/
│   ├── scripts/                    # Run scripts copied into the distribution root
│   └── config/                     # Packaging configs (launch4j)
├── docs/
│   ├── user-guide.md               # End-user guide
│   └── images/                     # Images used by the docs
├── build.gradle.kts                # Build, packaging, and frontend integration
└── proguard-rules.pro              # ProGuard obfuscation rules
```

The distribution zip has a flat layout: `app/`, `jre/`, `binary/` and the run scripts at its root. The app
looks for `binary/` relative to its working directory, so keep that layout when changing packaging.

## Getting Started

Requirements: JDK 17+. Node.js is downloaded automatically by Gradle.

```bash
./gradlew run                          # Build the web UI and start the server
./gradlew run -PskipWebuiBuild=true    # Start without rebuilding the web UI
./gradlew test                         # Run tests

./gradlew packageWindows               # Windows zip with bundled JRE
./gradlew packageMac                   # macOS zip with bundled JRE
./gradlew packageLinux                 # Linux zip with bundled JRE
./gradlew distZip                      # Zip without a JRE
```

Frontend-only development:

```bash
cd webui
npm install
npm start                              # Dev server at http://localhost:4200
```

## Java Code Style

Follow standard Java conventions ([Oracle Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)).
The main rules:

**Formatting**
- 4 spaces for indentation, no tabs.
- Opening brace on the same line; always use braces, even for one-line `if`/`for`.
- Keep lines under 120 characters.
- One top-level class per file.
- No wildcard imports (`import java.util.*`).

**Naming**

| Element            | Style            | Example                 |
|--------------------|------------------|-------------------------|
| Package            | lowercase        | `org.thingai.app.api`   |
| Class / Interface  | PascalCase       | `ScheduleHandler`       |
| Method / Variable  | camelCase        | `generateSchedule()`    |
| Constant           | UPPER_SNAKE_CASE | `MAX_TEAMS`             |

**Practices**
- Target Java 17. Don't use language features from newer versions.
- Keep endpoint classes in `api/` thin; put business logic in `scoringservice/`.
- Put event-specific rules in `eventimpl/<event>/`, not in `scoringservice/`.
- Log with `ILog` (from `applicationbase.jar`), not `System.out`.
- Don't swallow exceptions; log them or pass them to the caller.
- Write Javadoc for public classes and methods that aren't self-explanatory.

## Frontend Code Style

The Angular project uses Prettier (see `webui/package.json`). Run it before committing frontend changes.

## Git Workflow

1. Branch from `main`: `feat/<short-name>`, `fix/<short-name>` or `refactor/<short-name>`.
2. Keep commits small and focused. Use the format `type: summary`, for example:
   ```text
   feat: add match timer pause
   fix: correct ranking tie-break order
   ```
   Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`.
3. Make sure `./gradlew build` passes.
4. Open a pull request to `main` describing what changed and why.
