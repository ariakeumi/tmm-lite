# Architecture

## Package Structure

All code lives under `org.tinymediamanager.*`:

| Package        | Responsibility                                              |
|----------------|-------------------------------------------------------------|
| `core`         | Business logic, data models, entity lifecycle               |
| `ui`           | Swing-based user interface components                       |
| `scraper`      | Metadata providers and external data fetching               |
| `thirdparty`   | Integration with external tools                             |
| `cli`          | Command-line interface                                      |

Strictly separate concerns between modules. Movie and TV show logic must not bleed into each other.

## Event-Driven Model

UI and business logic communicate via `PropertyChangeSupport` (wrapped by `AbstractModelObject`).
Background tasks use `TmmThreadPool` / `TmmTaskManager`. UI updates always dispatch on the EDT.

## Movie Module (`org.tinymediamanager.core.movie`)

| Role             | Class                  |
|------------------|------------------------|
| Entity           | `Movie` (extends `MediaEntity`) |
| Movie set entity | `MovieSet`             |
| Settings         | `MovieSettings`        |
| Module manager   | `MovieModuleManager`   |

Key capabilities: movie sets, multiple video files per movie, NFO connectors for Kodi / Emby / Jellyfin / MediaPortal.
NFO connectors are in `core.movie.connector`.

## TV Show Module (`org.tinymediamanager.core.tvshow`)

| Role               | Class                                            |
|--------------------|--------------------------------------------------|
| Entities           | `TvShow`, `TvShowSeason`, `TvShowEpisode`        |
| Settings           | `TvShowSettings`                                 |
| Module manager     | `TvShowModuleManager`                            |

Key capabilities: multi-episode files, episode groups, season-level metadata.

## Scraper Module (`org.tinymediamanager.scraper`)

- Implement the appropriate interface (`IMovieMetadataProvider`, etc.)
- Use `MediaProviders` registry for discovery
- Return `MediaMetadata` for results, `MediaSearchResult` for search hits
- Support multiple ID types (IMDB, TMDB, TVDB, …) via `MediaIdUtil`
- Handle rate limiting and API quotas; cache aggressively to minimize API calls

## Backward Compatibility

- NFO file formats must never break without a migration path
- Database schema changes require upgrade tasks in `UpgradeTasks.java` (and the module-level equivalent, e.g., `MovieUpgradeTasks.java`)
- Support legacy configurations; document any breaking changes explicitly
