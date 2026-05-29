# AGENTS.md

## Project Overview

tinyMediaManager is a full-featured media management tool written in Java designed to organize and clean up media
libraries. It manages metadata, artwork, and file structures for media center software like Kodi, Plex, Emby, and
Jellyfin.

**Key Technologies:**

- **Language:** Java 17+ (target: Java 17, runtime: Java 21)
- **Build Tool:** Apache Maven
- **UI Framework:** Swing with FlatLaf (version 3.6.2)
- **Persistence:** H2 MVStore database
- **JSON Processing:** Jackson (version 2.20.0)
- **HTTP Client:** OkHttp (version 4.12.0)
- **Logging:** SLF4J (version 2.0.9) with Logback

## Core Principles

- Think before coding. State your assumptions out loud. If the request is ambiguous, ask. If a simpler approach exists, push back. Stop when you are confused, name what is unclear, do not just pick one interpretation and run.
- Simplicity first. Write the minimum code that solves the problem. No speculative abstractions. No flexibility nobody asked for. The test: would a senior engineer call this overcomplicated.
- Surgical changes. Touch only what the task requires. Do not improve neighboring code. Do not refactor what is not broken. Every changed line should trace back to the request.
- Goal-driven execution. Turn vague instructions into verifiable targets before writing a line. “Add validation” becomes “write tests for invalid inputs, then make them pass.”

### 1. Stability First

- **DO NOT** modify existing, stable code unless explicitly requested
- **DO NOT** refactor working code without explicit permission
- Prioritize backwards compatibility and data integrity
- Test thoroughly before proposing changes
- Consider the impact on existing user data and configurations

### 2. Preserve Existing Architecture

- Follow the established package structure: `org.tinymediamanager.*`
- Respect the separation of concerns:
  - `core` - Business logic and data models
  - `ui` - User interface components (Swing-based)
  - `scraper` - Metadata providers and scraping functionality
  - `thirdparty` - Integration with external tools
  - `cli` - Command-line interface
- Maintain the existing event-driven architecture using PropertyChangeSupport
- Use the established threading model (TmmThreadPool, TmmTaskManager)
- Avoid duplicate code; reuse existing utilities and patterns
- Strictly split responsibilities between modules (e.g., Movie vs. TV Show)

### 3. Code Style and Formatting

#### General Style

- **Indentation:** 2 spaces (NOT tabs)
- **Line Length:** Keep lines reasonable, prefer readability
- **Braces:** Opening brace on same line, closing brace on new line
- **Imports:** Organize logically, static imports first, then standard imports

#### Naming Conventions

- **Classes:** PascalCase (e.g., `MovieMetadataProvider`, `TvShowSettings`)
- **Interfaces:** PascalCase with 'I' prefix (e.g., `IMovieMetadataProvider`, `IMediaProvider`)
- **Methods:** camelCase (e.g., `getMetadata()`, `firePropertyChange()`)
- **Variables:** camelCase (e.g., `movieList`, `dataSource`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `DATA_FOLDER`, `CONTENT_FOLDER`)
- **Packages:** lowercase (e.g., `org.tinymediamanager.core.movie`)

#### File Headers

Every Java file MUST start with the Apache License 2.0 header:

```java
/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.your.package;
```

### 4. Documentation Standards

#### JavaDoc Requirements

**EVERY** class, interface, method, and public field MUST have JavaDoc documentation.

##### Class JavaDoc

```java
/**
 * The Class {@link ClassName} provides functionality for [purpose].
 * <p>
 * [Additional details about the class, its responsibilities, and usage patterns]
 * </p>
 *
 * @author [Your Name]
 * @since [Version when introduced]
 */
public class ClassName {
```

##### Method JavaDoc

```java
/**
 * [Brief description of what the method does].
 * <p>
 * [Additional details about behavior, side effects, or important notes]
 * </p>
 *
 * @param paramName
 *          [description of parameter]
 * @param anotherParam
 *          [description of parameter]
 * @return [description of return value]
 * @throws ExceptionType
 *           [when and why this exception is thrown]
 */
public ReturnType methodName(Type paramName, Type anotherParam) throws ExceptionType {
```

##### Field JavaDoc

```java
/** [Brief description of the field and its purpose] */
private String fieldName;
```

#### Inline Comments

- Add comments for complex logic or non-obvious code
- Explain WHY, not WHAT (the code shows what, comments explain why)
- Use `//` for single-line comments
- Place comments above the code they describe
- Keep comments up-to-date when code changes

Example:

```java
// Calculate the aspect ratio manually because some files have incorrect metadata
// and we need to ensure compatibility with Kodi's display requirements
if(width >0&&height >0){
aspectRatio =(double)width /height;
}
```

### 5. Design Patterns and Best Practices

#### Model Objects

- Extend `AbstractModelObject` for observable entities
- Use PropertyChangeSupport for event notification
- Implement proper equals(), hashCode(), and toString() methods
- Use Jackson annotations for JSON serialization (@JsonProperty, @JsonDeserialize)

Example:

```java
/**
 * The Class {@link Movie} represents a movie entity with all its metadata.
 *
 * @author Manuel Laggner
 */
public class Movie extends AbstractModelObject {

  @JsonProperty private String title = "";

  /**
   * Sets the title and fires a property change event.
   *
   * @param newTitle
   *          the new title
   */
  public void setTitle(String newTitle) {
    String oldValue = this.title;
    this.title = newTitle;
    firePropertyChange("title", oldValue, newTitle);
  }
}
```

#### Constants

- Define all constant strings in the `Constants` class
- Use constants instead of magic strings or numbers
- Group related constants together

#### Exception Handling

- Use specific exception types (e.g., `ScrapeException`, `MissingIdException`)
- Always log exceptions with context
- Handle exceptions at appropriate levels
- Don't swallow exceptions silently without good reason

```java
try{
        // operation
        }
        catch(ScrapeException e){
        LOGGER.

error("Failed to scrape metadata for movie: {} - {}",movie.getTitle(),e.

getMessage());
        throw new

RuntimeException("Scraping failed",e);
}
```

#### Resource Management

- Use try-with-resources for AutoCloseable objects
- Close streams, connections, and files properly
- Clean up temporary resources in finally blocks

#### Threading

- Use TmmThreadPool for background tasks
- Use TmmTaskManager to manage long-running operations
- Always update UI on the Event Dispatch Thread (EDT)
- Use SwingUtilities.invokeLater() or SwingUtilities.invokeAndWait() for UI updates

```java
TmmThreadPool.getInstance().

submit(() ->{
        // background work

        SwingUtilities.

invokeLater(() ->{
        // update UI
        });
        });
```

### 6. Localization

- Use `TmmResourceBundle` for all user-facing strings
- Never hardcode user-visible text
- Keys should be descriptive: `"Module.action.description"`
- Provide English fallbacks

```java
String message = TmmResourceBundle.getString("movie.scrape.success");
```

### 7. Settings and Persistence

- Settings extend `AbstractSettings`
- Use JSON for configuration persistence
- Validate settings on load
- Provide sensible defaults
- Don't store sensitive data in plain text (use encryption helpers)

### 8. Scraper Development

- Implement appropriate interface (e.g., `IMovieMetadataProvider`)
- Extend `MediaMetadata` for results
- Use `MediaSearchResult` for search results
- Handle rate limiting and API quotas
- Cache aggressively to reduce API calls
- Respect robots.txt and API terms of service
- Include proper error handling and logging

### 9. UI Development

- Use FlatLaf components and styling
- Follow Swing best practices (EDT for updates)
- Use proper layout managers (GroupLayout, MigLayout, BorderLayout)
- Maintain responsive UI (don't block EDT)
- Use binding frameworks where appropriate (BeansBinding)
- Support high DPI displays
- Ensure accessibility (keyboard navigation, screen readers)

### 10. Testing

- Write unit tests for business logic
- Integration tests for scrapers and external services
- UI tests for critical user workflows
- Test with different OS environments (Windows, Linux, macOS)
- Test with different data scenarios (empty, corrupted, large datasets)
- Run unit tests with Maven using `-DskipTests=false` as all unit tests are disabled by default

### 11. Performance Considerations

- Lazy load data when possible
- Use pagination for large datasets
- Cache expensive operations
- Profile before optimizing
- Consider memory footprint (users may have large libraries)
- Optimize database queries (H2 MVStore)
- Use appropriate data structures (ObservableCopyOnWriteArrayList for thread-safe collections)

### 12. Security

- Validate all user input
- Sanitize file paths to prevent directory traversal
- Use secure connections (HTTPS) for API calls
- Don't log sensitive information (API keys, passwords)
- Use the encryption utilities (AesUtil) for sensitive data

### 13. Backward Compatibility

- Never break existing NFO file formats without migration
- Provide upgrade tasks for database schema changes (see UpgradeTasks.java)
- Support legacy configurations
- Document breaking changes clearly
- Test with real user data from previous versions

### 14. Git and Version Control

- Commit messages should be clear and concise
- Follow conventional commits format when possible
- Target the `devel` branch for pull requests
- Squash granular commits before merging
- Commit messages are sourced into the changelog

Commit message format:

```
[Category] brief description of the change

- detailed point 1
- detailed point 2
```

Categories: Movies, TV shows, UI, Scraper, Performance, NFO, Renamer, etc.

### 15. Specific Module Guidelines

#### Movie Module (`org.tinymediamanager.core.movie`)

- Core entity: `Movie` (extends MediaEntity)
- Settings: `MovieSettings`
- Module manager: `MovieModuleManager`
- Support movie sets functionality
- Handle multiple video files per movie
- Support custom NFO formats (Kodi, MediaPortal, Emby, etc.)

#### TV Show Module (`org.tinymediamanager.core.tvshow`)

- Core entities: `TvShow`, `TvShowSeason`, `TvShowEpisode`
- Settings: `TvShowSettings`
- Module manager: `TvShowModuleManager`
- Support multi-episode files
- Handle episode groups
- Support season-level metadata

#### Scraper Module (`org.tinymediamanager.scraper`)

- Implement interface contracts precisely
- Use MediaProviders registry
- Support multiple ID types (IMDB, TMDB, TVDB, etc.)
- Use MediaIdUtil for ID conversion
- Handle API rate limiting gracefully
- Provide fallback options

### 16. Common Utilities

- `Utils` - General utility methods
- `StrgUtils` - String manipulation utilities
- `ImageUtils` - Image processing and manipulation
- `MediaFileHelper` - File and path operations
- `LanguageUtils` - Language code conversions
- `TmmDateFormat` - Date formatting utilities

### 17. When Generating New Code

**DO:**

- Add comprehensive JavaDoc to ALL new code
- Follow the existing code style precisely
- Use existing utility classes and patterns
- Consider thread safety
- Handle errors gracefully
- Add logging at appropriate levels (TRACE, DEBUG, INFO, WARN, ERROR)
- Test with edge cases
- Update relevant documentation

**DO NOT:**

- Modify unrelated code
- Change existing stable APIs without discussion
- Add dependencies without justification
- Break existing functionality
- Ignore performance implications
- Skip error handling
- Leave debug code in production
- Violate the established architecture

### 18. Code Review Checklist

Before submitting code, verify:

- [ ] All classes and methods have proper JavaDoc
- [ ] License header is present
- [ ] Code follows the 2-space indentation
- [ ] No unused imports or variables
- [ ] Proper exception handling
- [ ] Logging is appropriate
- [ ] Thread safety is considered
- [ ] UI updates are on EDT
- [ ] Settings are persisted correctly
- [ ] Localization is used for user-facing text
- [ ] Backward compatibility is maintained
- [ ] Performance impact is acceptable
- [ ] Code compiles without warnings

### 19. Logging Guidelines

Use SLF4J with appropriate log levels:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(YourClass.class);

// TRACE: Very detailed information (e.g., variable values)
LOGGER.

trace("Processing movie: {}",movie);

// DEBUG: Detailed information for debugging
LOGGER.

debug("Scraper {} returned {} results",scraperName, results.size());

        // INFO: General informational messages
        LOGGER.

info("Started update of data source: {}",dataSource);

// WARN: Warning messages for recoverable issues
LOGGER.

warn("Could not find poster for movie: {}",movie.getTitle());

        // ERROR: Error messages for serious problems
        LOGGER.

error("Failed to save movie data: {}",movie.getTitle(),exception);
```

### 20. Special Considerations

#### Platform-Specific Code

- Use `TmmOsUtils` for OS detection
- Use `SystemUtils` from Apache Commons Lang
- Test on all supported platforms (Windows, Linux, macOS)
- Handle path separators correctly
- Consider file system case sensitivity

#### External Dependencies

- Prefer existing dependencies over adding new ones
- Justify any new dependency additions
- Check license compatibility (Apache 2.0)
- Consider library size and maintenance status

#### Database Operations

- Use H2 MVStore for all persistent data
- Handle database corruption gracefully
- Provide backup and restore functionality
- Consider database migration for schema changes

#### Web Interface Development

- **Technology Stack:** React with TypeScript, Ant Design UI components, Vite build tool
- **Location:** `src/main/webapp/`
- **Build Output:** `src/main/resources/webapp/`

**IMPORTANT - Always rebuild and verify:**

When making ANY changes to the web interface:

1. **Always rebuild the package** after changes:
   ```bash
   cd src/main/webapp
   npm run build
   ```

2. **Check for syntax errors** before committing:
   ```bash
   npm run lint
   npm run type-check  # or tsc --noEmit
   ```

3. **Test in the browser** - verify the changes work in the running application

4. **Common issues to check:**

- Missing imports
- Unused variables
- Type mismatches
- API endpoint changes need corresponding backend updates
- Ensure all Ant Design components are properly imported
- Check that all hooks (useState, useEffect, etc.) are used correctly

**Development Workflow:**

- Use `npm run dev` for development with hot-reload
- Use `npm run build` for production build
- Backend proxies `/api` requests during development (see `vite.config.ts`)

**Code Style:**

- Follow TypeScript/React best practices
- Use functional components with hooks
- Proper TypeScript typing (avoid `any` when possible)
- Use Ant Design components consistently
- Keep API calls in `src/services/api.ts`

**API Integration:**

- All API endpoints under `/api/v2/` (v2 API) or `/api/` (legacy)
- Use axios instance from `api.ts` with automatic API key injection
- Handle loading states and errors properly
- Show user-friendly error messages

## Summary

When working with tinyMediaManager:

1. **Stability and backwards compatibility are paramount**
2. **Follow the existing code style exactly (2-space indentation)**
3. **Document everything with comprehensive JavaDoc**
4. **Don't touch working code unless necessary**
5. **Think about the users' data and existing configurations**
6. **Test thoroughly on all supported platforms**
7. **When in doubt, ask - don't assume**

This is a mature, production-grade application with thousands of users. Every change should be made with care and
consideration for the existing user base and their data.
