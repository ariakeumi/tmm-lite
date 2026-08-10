# Testing

## Running Tests

Unit tests are disabled by default. Enable them explicitly:

```
mvn test -DskipTests=false
```

## Test Scope

- Unit tests: business logic in `core`
- Integration tests: scrapers and external services
- UI tests: critical user workflows

## Platform Coverage

Test on Windows, Linux, and macOS. Pay attention to file system case sensitivity and path separator differences.
Test with edge-case data: empty libraries, corrupted files, and large datasets.
