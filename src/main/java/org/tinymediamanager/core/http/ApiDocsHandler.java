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
package org.tinymediamanager.core.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.ReleaseInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * the class {@link ApiDocsHandler} offers a self-contained OpenAPI (swagger style) description of the remote control API - created without any
 * additional dependencies
 *
 * @author Manuel Laggner
 */
public class ApiDocsHandler implements HttpHandler {
  private static final String API_KEY_HEADER    = "api-key";

  private static final String INTRO             = """
      The tinyMediaManager remote control API lets you trigger the actions of tinyMediaManager via simple HTTP POST calls.

      Every command consists of three parts:
      * **action** - which action should be performed
      * **scope** - which entities (movies / TV shows / episodes) should be processed
      * **args** - extra arguments for the action

      A single HTTP call can contain multiple commands (as a JSON array or a single JSON object) - this is especially
      useful when you work with a temporary scope such as *newly found* items.

      **The commands are NOT executed in the order they have been sent in, but in a logical order:**
      1. update the library (`update`, `readNfo`, `reloadMediaInfo`, `calculateChecksum`, `detectAspectRatio`)
      2. scrape (`scrape`, `fetchRatings`, `assignMovieSet`)
      3. download tasks (`downloadTrailer`, `downloadSubtitle`, `downloadMissingArtwork`, `downloadTheme`, `cleanupArtwork`)
      4. rename (`rename`)
      5. export (`export`, `writeNfo`)
      6. cleanup unwanted leftover files (`cleanup`)
      7. post process (`postProcess`)
      8. notify/sync external services (`updateKodi`, `syncTrakt`, `syncSimkl`)

      Multiple HTTP calls are queued up and executed one after another.
      """;

  private static final String SCOPE_DESCRIPTION = """
      Which entities should be processed:
      * `new` (default for `/api/movie` & `/api/tvshow`) - the entities which have been found by the `update` commands of the *same* call
      * `all` (default for `/api/movieset`) - all entities of the library
      * `unscraped` - all entities which have not been scraped yet
      * `path` - the entities at the given paths (paths given in `args`)
      * `dataSource` - the entities of the given data sources (paths or indices given in `args`)
      * `name` (movie sets only) - the movie sets with the given titles (titles given in `args`)

      Only used with the `update` command (data source scope):
      * `all` (default) - all data sources
      * `single` - the data sources with the given 1-based index (indices given in `args`)
      * `path` - the data sources at the given paths (paths given in `args`)
      * `show` - (TV shows only) the shows at the given show folder paths (paths given in `args`)
      """;

  private static final String MOVIE_ACTIONS     = """
      Executes movie related commands.

      | action | executed in phase | extra args | description |
      |---|---|---|---|
      | `update` | 1 - update library | – | scan the data sources for new/changed movies |
      | `readNfo` | 1 - update library | – | force re-read the NFO files into the database (this may overwrite existing data!) |
      | `reloadMediaInfo` | 1 - update library | – | re-read the media info of all media files |
      | `calculateChecksum` | 1 - update library | `type`: `crc32` (default), `phash` or `all` | calculate the CRC32 and/or the perceptual hash of the main video file (only if not already set) |
      | `detectAspectRatio` | 1 - update library | – | detect the aspect ratio of the main video files |
      | `scrape` | 2 - scrape | `scraper`: id of the metadata scraper to use | scrape the metadata of the movies |
      | `fetchRatings` | 2 - scrape | – | fetch the ratings from the configured rating sources |
      | `assignMovieSet` | 2 - scrape | – | assign the movies to their movie sets (needs the scraped `movie set` data) |
      | `downloadTrailer` | 3 - download | `onlyMissing`: `true` (default) / `false` | download trailers |
      | `downloadSubtitle` | 3 - download | `language`: subtitle language, `onlyMissing`: `true` (default) / `false` | search and download subtitles |
      | `downloadMissingArtwork` | 3 - download | `scraper`: comma separated list of artwork scraper ids | download missing artwork |
      | `rename` | 4 - rename | `profile`: name of the renamer profile to use | rename and move the files of the movies |
      | `export` | 5 - export | `template`: name of the export template, `exportPath`: destination folder (both required) | export the movies with an export template |
      | `writeNfo` | 5 - export | – | write the movie data back to the NFO files next to the media files |
      | `cleanup` | 6 - cleanup | `dryRun`: `false` (default) / `true` | delete unwanted files (configured cleanup file types) in the folders of the movies - `dryRun` only logs the found files |
      | `postProcess` | 7 - post process | – | execute all configured post-processing scripts |
      | `updateKodi` | 8 - external | – | tell Kodi to reload the movies out of the NFO files (needs a configured Kodi host) |
      | `syncTrakt` | 8 - external | `collection`, `watched`, `rating`: `true` (default) / `false` | synchronize the movies to Trakt.tv |
      | `syncSimkl` | 8 - external | – | synchronize the watched state of the movies to Simkl.com |
      """;

  private static final String TVSHOW_ACTIONS    = """
      Executes TV show related commands. Actions work on TV shows and/or episodes depending on the resolved scope.

      | action | executed in phase | extra args | description |
      |---|---|---|---|
      | `update` | 1 - update library | – | scan the data sources for new/changed TV shows |
      | `readNfo` | 1 - update library | – | force re-read the NFO files of shows/seasons/episodes into the database (this may overwrite existing data!) |
      | `reloadMediaInfo` | 1 - update library | – | re-read the media info of all media files |
      | `calculateChecksum` | 1 - update library | `type`: `crc32` (default), `phash` or `all` | calculate the CRC32 and/or the perceptual hash of the main video files of the episodes (only if not already set) |
      | `detectAspectRatio` | 1 - update library | – | detect the aspect ratio of the main video files |
      | `scrape` | 2 - scrape | – | scrape the metadata of the TV shows and episodes (also syncs to Trakt if enabled) |
      | `fetchRatings` | 2 - scrape | – | fetch the ratings from the configured rating sources |
      | `downloadTrailer` | 3 - download | `onlyMissing`: `true` (default) / `false` | download trailers |
      | `downloadSubtitle` | 3 - download | `language`: subtitle language, `onlyMissing`: `true` (default) / `false` | search and download subtitles |
      | `downloadMissingArtwork` | 3 - download | `scraper`: comma separated list of artwork scraper ids | download missing artwork |
      | `downloadTheme` | 3 - download | `onlyMissing`: `true` (default) / `false` | download TV show theme files |
      | `rename` | 4 - rename | `profile`: name of the renamer profile to use | rename and move the files of the shows/episodes |
      | `export` | 5 - export | `template`: name of the export template, `exportPath`: destination folder (both required) | export the TV shows with an export template |
      | `writeNfo` | 5 - export | – | write the show/season/episode data back to the NFO files next to the media files |
      | `cleanup` | 6 - cleanup | `dryRun`: `false` (default) / `true` | delete unwanted files (configured cleanup file types) in the folders of the shows/episodes - `dryRun` only logs the found files |
      | `postProcess` | 7 - post process | – | execute all configured post-processing scripts for shows and episodes |
      | `updateKodi` | 8 - external | `full`: `true` (default) / `false` | tell Kodi to reload the shows/episodes out of the NFO files (`full` also refreshes all episodes of a show) - needs a configured Kodi host |
      | `syncTrakt` | 8 - external | `collection`, `watched`, `rating`: `true` (default) / `false` | synchronize the TV shows to Trakt.tv |
      | `syncSimkl` | 8 - external | – | synchronize the watched state of the TV shows to Simkl.com |
      """;

  private static final String MOVIESET_ACTIONS  = """
      Executes movie set related commands.

      | action | executed in phase | extra args | description |
      |---|---|---|---|
      | `scrape` | 2 - scrape | `scraper`: id of the movie set scraper to use | scrape the metadata of the movie sets |
      | `downloadMissingArtwork` | 3 - download | `scraper`: comma separated list of movie set scraper ids | download missing artwork of the movie sets |
      | `cleanupArtwork` | 3 - download | – | move existing artwork of the movie sets into the correct folders (according to the settings) |
      | `rename` | 4 - rename | `profile`: name of the renamer profile to use | rename and move the files of all movies in the movie sets |
      | `export` | 5 - export | `template`: name of the export template, `exportPath`: destination folder (both required) | export the movie sets with an export template |
      | `writeNfo` | 5 - export | – | write the movie set data back to the NFO files (needs configured movie set NFO filenames) |
      | `postProcess` | 7 - post process | – | execute all configured post-processing scripts for movie sets and their movies |
      | `updateKodi` | 8 - external | – | tell Kodi to reload the movies of the movie sets out of the NFO files (needs a configured Kodi host) |
      | `syncTrakt` | 8 - external | `collection`, `watched`, `rating`: `true` (default) / `false` | synchronize the movies of the movie sets to Trakt.tv |
      """;

  private final ObjectMapper  objectMapper;
  private final String        apiDoc;

  public ApiDocsHandler() {
    objectMapper = new ObjectMapper();
    apiDoc = createApiDoc();
  }

  @Override
  public void handle(HttpExchange httpExchange) throws IOException {
    int responseCode;
    byte[] body;

    if ("GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
      responseCode = 200;
      body = apiDoc.getBytes(StandardCharsets.UTF_8);
    }
    else {
      responseCode = 405;
      body = "{\"message\":\"only GET supported\"}".getBytes(StandardCharsets.UTF_8);
    }

    httpExchange.getResponseHeaders().add("Content-Type", "application/json");
    httpExchange.sendResponseHeaders(responseCode, body.length);
    try (OutputStream out = httpExchange.getResponseBody()) {
      out.write(body);
    }
  }

  private String createApiDoc() {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("openapi", "3.0.3");

    ObjectNode info = root.putObject("info");
    info.put("title", "tinyMediaManager remote control API");
    info.put("version", StringUtils.defaultIfBlank(ReleaseInfo.getVersion(), "5"));
    info.put("description", INTRO);

    ObjectNode components = root.putObject("components");

    // security
    root.putArray("security").addObject().putArray("api_key");
    ObjectNode apiKey = components.putObject("securitySchemes").putObject("api_key");
    apiKey.put("type", "apiKey");
    apiKey.put("in", "header");
    apiKey.put("name", API_KEY_HEADER);
    apiKey.put("description", "the API key as configured in the settings of tinyMediaManager");

    createSchemas(components.putObject("schemas"));

    ObjectNode paths = root.putObject("paths");
    createCommandsPath(paths, "/api/movie", "movie", "Queue movie commands", MOVIE_ACTIONS);
    createCommandsPath(paths, "/api/tvshow", "tvshow", "Queue TV show commands", TVSHOW_ACTIONS);
    createCommandsPath(paths, "/api/movieset", "movieset", "Queue movie set commands", MOVIESET_ACTIONS);

    ObjectNode docsGet = paths.putObject("/api/docs").putObject("get");
    docsGet.putArray("tags").add("docs");
    docsGet.put("summary", "Get the description of this API");
    docsGet.put("description", "Returns this OpenAPI (swagger style) document as JSON");
    ObjectNode docsResponses = docsGet.putObject("responses");
    docsResponses.putObject("200")
        .put("description", "the OpenAPI document")
        .putObject("content")
        .putObject("application/json")
        .putObject("schema")
        .put("type", "object");
    docsResponses.putObject("403").put("description", "invalid API key");

    return root.toPrettyString();
  }

  private void createSchemas(ObjectNode schemas) {
    ObjectNode command = schemas.putObject("Command");
    command.put("type", "object");
    command.putArray("required").add("action");
    ObjectNode commandProps = command.putObject("properties");
    commandProps.putObject("action")
        .put("type", "string")
        .put("description", "the action to execute - see the endpoint description for all supported actions");
    commandProps.putObject("scope").put("$ref", "#/components/schemas/CommandScope");
    ObjectNode args = commandProps.putObject("args");
    args.put("type", "object").put("description", "extra arguments for the action");
    args.putObject("additionalProperties").put("type", "string");

    ObjectNode scope = schemas.putObject("CommandScope");
    scope.put("type", "object");
    ObjectNode scopeProps = scope.putObject("properties");
    scopeProps.putObject("name").put("type", "string").put("description", SCOPE_DESCRIPTION);
    scopeProps.putObject("args")
        .put("type", "array")
        .put("description", "arguments of the scope (paths or indices)")
        .putObject("items")
        .put("type", "string");

    ObjectNode response = schemas.putObject("TmmResponse");
    response.put("type", "object");
    response.putObject("properties").putObject("message").put("type", "string");
  }

  private void createCommandsPath(ObjectNode paths, String path, String tag, String summary, String actionTable) {
    ObjectNode post = paths.putObject(path).putObject("post");
    post.putArray("tags").add(tag);
    post.put("operationId", "commands_" + tag);
    post.put("summary", summary);
    post.put("description", actionTable);

    ObjectNode content = post.putObject("requestBody").put("required", true).putObject("content").putObject("application/json");

    ObjectNode schema = content.putObject("schema");
    ArrayNode oneOf = schema.putArray("oneOf");
    oneOf.addObject().put("type", "array").putObject("items").put("$ref", "#/components/schemas/Command");
    oneOf.addObject().put("$ref", "#/components/schemas/Command");

    ArrayNode example = content.putArray("example");
    if ("movieset".equals(tag)) {
      example.addObject().put("action", "scrape").putObject("scope").put("name", "all");
      example.addObject().put("action", "downloadMissingArtwork").putObject("scope").put("name", "all");
      example.addObject().put("action", "writeNfo").putObject("scope").put("name", "all");
    }
    else {
      ObjectNode update = example.addObject();
      update.put("action", "update");
      update.putObject("scope").put("name", "all");
      example.addObject().put("action", "scrape");
      example.addObject().put("action", "downloadMissingArtwork");
      example.addObject().put("action", "rename");
      example.addObject().put("action", "writeNfo");
      example.addObject().put("action", "postProcess");
    }

    ObjectNode responses = post.putObject("responses");
    responses.putObject("200")
        .put("description", "the commands have been queued for execution")
        .putObject("content")
        .putObject("application/json")
        .putObject("schema")
        .put("$ref", "#/components/schemas/TmmResponse");
    responses.putObject("403").put("description", "invalid API key");
  }
}
