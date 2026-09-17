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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * The class {@link ApiDocsHandlerTest} verifies the self-generated OpenAPI description offered by the {@link ApiDocsHandler}.
 */
public class ApiDocsHandlerTest {
  private HttpServer         server;
  private String             baseUrl;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Starts a plain HTTP server with only the docs handler on an ephemeral port.
   *
   * @throws Exception
   *           thrown if the server cannot be started
   */
  @Before
  public void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/docs", new ApiDocsHandler());
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /**
   * Stops the test server.
   */
  @After
  public void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  /**
   * Tests that a GET returns a valid OpenAPI document covering all API endpoints.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void getReturnsOpenApiDocument() throws Exception {
    HttpResponse<String> response = request("GET", "/api/docs");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-type").orElse("")).startsWith("application/json");

    JsonNode root = mapper.readTree(response.body());
    assertThat(root.path("openapi").asText()).isEqualTo("3.0.3");
    assertThat(root.path("info").path("title").asText()).contains("tinyMediaManager");
    assertThat(root.path("info").path("version").asText()).isNotBlank();
    assertThat(root.path("info").path("description").asText()).isNotBlank();
  }

  /**
   * Tests that the OpenAPI document describes all command endpoints and the docs endpoint itself.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void documentContainsAllEndpoints() throws Exception {
    JsonNode paths = documentPaths();

    assertThat(paths.has("/api/movie")).isTrue();
    assertThat(paths.has("/api/tvshow")).isTrue();
    assertThat(paths.has("/api/movieset")).isTrue();
    assertThat(paths.has("/api/docs")).isTrue();

    assertThat(paths.path("/api/movie").path("post").isObject()).isTrue();
    assertThat(paths.path("/api/tvshow").path("post").isObject()).isTrue();
    assertThat(paths.path("/api/movieset").path("post").isObject()).isTrue();
    assertThat(paths.path("/api/docs").path("get").isObject()).isTrue();

    // every command endpoint needs a request body and the queue/forbidden responses
    for (String endpoint : new String[] { "/api/movie", "/api/tvshow", "/api/movieset" }) {
      JsonNode post = paths.path(endpoint).path("post");
      assertThat(post.path("requestBody").path("required").asBoolean()).as(endpoint + " requires a body").isTrue();
      assertThat(post.path("responses").has("200")).as(endpoint + " documents 200").isTrue();
      assertThat(post.path("responses").has("403")).as(endpoint + " documents 403").isTrue();
    }
  }

  /**
   * Tests that the request body schema accepts a single command object as well as an array of commands.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void commandBodyAcceptsSingleAndMultipleCommands() throws Exception {
    JsonNode schema = documentPaths().path("/api/movie").path("post").path("requestBody").path("content").path("application/json").path("schema");

    JsonNode oneOf = schema.path("oneOf");
    assertThat(oneOf.isArray()).isTrue();
    assertThat(oneOf).hasSize(2);
    assertThat(oneOf.get(0).path("items").path("$ref").asText()).isEqualTo("#/components/schemas/Command");
    assertThat(oneOf.get(1).path("$ref").asText()).isEqualTo("#/components/schemas/Command");
  }

  /**
   * Tests that the examples are sane - the movieset default scope is {@code all}, the others use {@code update} first.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void examplesUseSensibleDefaultScopes() throws Exception {
    JsonNode paths = documentPaths();

    JsonNode movieExample = paths.path("/api/movie").path("post").path("requestBody").path("content").path("application/json").path("example");
    assertThat(movieExample.isArray()).isTrue();
    assertThat(movieExample.get(0).path("action").asText()).isEqualTo("update");

    JsonNode moviesetExample = paths.path("/api/movieset").path("post").path("requestBody").path("content").path("application/json").path("example");
    assertThat(moviesetExample.isArray()).isTrue();
    for (JsonNode command : moviesetExample) {
      assertThat(command.path("scope").path("name").asText()).isEqualTo("all");
    }
  }

  /**
   * Tests that the API key security scheme is described as a header named {@code api-key}.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void describesApiKeySecurityScheme() throws Exception {
    JsonNode root = mapper.readTree(request("GET", "/api/docs").body());
    JsonNode apiKey = root.path("components").path("securitySchemes").path("api_key");

    assertThat(apiKey.path("type").asText()).isEqualTo("apiKey");
    assertThat(apiKey.path("in").asText()).isEqualTo("header");
    assertThat(apiKey.path("name").asText()).isEqualTo("api-key");

    assertThat(root.path("security").isArray()).isTrue();
    assertThat(root.path("security").get(0).has("api_key")).isTrue();
  }

  /**
   * Tests that the command/scope/response schemas are present and the command action is required.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void describesCommandSchemas() throws Exception {
    JsonNode root = mapper.readTree(request("GET", "/api/docs").body());
    JsonNode schemas = root.path("components").path("schemas");

    assertThat(schemas.has("Command")).isTrue();
    assertThat(schemas.has("CommandScope")).isTrue();
    assertThat(schemas.has("TmmResponse")).isTrue();

    JsonNode required = schemas.path("Command").path("required");
    assertThat(required.isArray()).isTrue();
    assertThat(required.get(0).asText()).isEqualTo("action");
    assertThat(schemas.path("Command").path("properties").has("scope")).isTrue();
    assertThat(schemas.path("Command").path("properties").has("args")).isTrue();
    assertThat(schemas.path("TmmResponse").path("properties").path("message").path("type").asText()).isEqualTo("string");
  }

  /**
   * Tests that anything other than GET is rejected with 405.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void onlyGetIsSupported() throws Exception {
    HttpResponse<String> response = request("POST", "/api/docs", "{}");

    assertThat(response.statusCode()).isEqualTo(405);
    assertThat(response.body()).contains("only GET supported");
  }

  private JsonNode documentPaths() throws Exception {
    return mapper.readTree(request("GET", "/api/docs").body()).path("paths");
  }

  private HttpResponse<String> request(String method, String path) throws Exception {
    return request(method, path, null);
  }

  private HttpResponse<String> request(String method, String path, String body) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));

    if (body != null) {
      builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    }
    else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }

    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
