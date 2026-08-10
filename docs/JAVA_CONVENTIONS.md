# Java Conventions

## License Header

Every Java file must begin with the Apache License 2.0 header (copyright year: `2012 - 2026 Manuel Laggner`).
See `src/main/java/org/tinymediamanager/core/movie/entities/Movie.java:1` for the canonical header.

## Naming Conventions

- **Classes:** PascalCase (`MovieMetadataProvider`, `TvShowSettings`)
- **Interfaces:** PascalCase with `I` prefix (`IMovieMetadataProvider`, `IMediaProvider`)
- **Methods:** camelCase (`getMetadata()`, `firePropertyChange()`)
- **Variables:** camelCase (`movieList`, `dataSource`)
- **Constants:** UPPER_SNAKE_CASE (`DATA_FOLDER`, `CONTENT_FOLDER`)
- **Packages:** lowercase (`org.tinymediamanager.core.movie`)

## Code Style

- 2-space indentation (no tabs)
- Opening brace on the same line, closing brace on a new line
- Imports: static imports first, then grouped by origin

## JavaDoc

Every class, interface, method, and public field must have JavaDoc.

**Class:**
```java
/**
 * The Class {@link ClassName} provides functionality for [purpose].
 *
 * @author Manuel Laggner
 * @since [version]
 */
```

**Method:**
```java
/**
 * [Brief description].
 *
 * @param paramName [description]
 * @return [description]
 * @throws ExceptionType [when/why]
 */
```

**Field:** `/** [Brief description] */`

## Model Objects

Observable entities extend `AbstractModelObject` (see `src/main/java/org/tinymediamanager/core/AbstractModelObject.java`).
Use `firePropertyChange("propertyName", oldValue, newValue)` for all mutations.
See `src/main/java/org/tinymediamanager/core/movie/entities/Movie.java:140` for a complete example.

## Constants

Define constant strings in the `Constants` class — never use magic strings.
See `src/main/java/org/tinymediamanager/core/Constants.java`.

## Exception Handling

Use specific exception types (`ScrapeException`, `MissingIdException`). Log exceptions with context before rethrowing.
Never swallow exceptions silently.

## Threading

- Background tasks: `TmmThreadPool` / `TmmTaskManager`
- UI updates must always happen on the Event Dispatch Thread — use `SwingUtilities.invokeLater()`

## Localization

All user-visible strings go through `TmmResourceBundle.getString("module.key")`. Never hardcode UI text.
Key format: `"Module.action.description"`.

## Settings

Settings extend `AbstractSettings`. Store with JSON, provide sensible defaults, and never store sensitive data
in plain text — use `AesUtil` for encryption.

## Logging

Use SLF4J: `private static final Logger LOGGER = LoggerFactory.getLogger(YourClass.class);`

| Level   | When to use                                                      |
|---------|------------------------------------------------------------------|
| `TRACE` | Variable values, very granular flow tracing                      |
| `DEBUG` | Scraper results, decision points                                 |
| `INFO`  | Data source updates, major operations starting                   |
| `WARN`  | Recoverable issues (missing poster, invalid but parseable data)  |
| `ERROR` | Failures that affect user data or key functionality              |

Never log API keys, passwords, or other sensitive values.

## Resource Management

Use try-with-resources for all `AutoCloseable` objects. Clean up temporary resources in `finally` blocks.

## Platform Utilities

- OS detection: `TmmOsUtils` / `SystemUtils` (Apache Commons Lang)
- Always handle path separators correctly — test on Windows, Linux, and macOS

## Key Utility Classes

| Class             | Purpose                              |
|-------------------|--------------------------------------|
| `Utils`           | General-purpose helpers              |
| `StrgUtils`       | String manipulation                  |
| `ImageUtils`      | Image processing                     |
| `MediaFileHelper` | File and path operations             |
| `LanguageUtils`   | Language code conversions            |
| `TmmDateFormat`   | Date formatting                      |
