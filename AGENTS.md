# Project

tinyMediaManager is a Java media management tool that organizes metadata, artwork, and file structures for media center software (Kodi, Plex, Emby, Jellyfin).

## Commands

- Build: `mvn package`
- Test: `mvn test -DskipTests=false` (tests are skipped by default)

## Core Principles

- Think before coding. Ask when ambiguous. Push back on over-engineering. Name what is unclear — do not pick one interpretation and run.
- Surgical changes only. Every changed line must trace back to the request. Do not refactor working code.
- Backwards compatibility and data integrity are paramount. This application has thousands of users with existing libraries.

## Reference

- Java conventions, naming, JavaDoc, threading, logging: [docs/JAVA_CONVENTIONS.md](docs/JAVA_CONVENTIONS.md)
- Architecture, modules, domain model: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Testing: [docs/TESTING.md](docs/TESTING.md)
- Web UI (React/TypeScript): [docs/WEB_UI.md](docs/WEB_UI.md)
- Git workflow and commit format: [docs/GIT.md](docs/GIT.md)

Always answer in the language of the question. Code and comments must be in English. Use English for all commit
messages, PRs, and issues.
