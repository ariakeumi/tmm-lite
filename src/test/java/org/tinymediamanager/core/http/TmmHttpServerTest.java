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

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.tinymediamanager.core.BasicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/**
 * The class {@link TmmHttpServerTest} verifies the HTTP server behavior, especially that the API key check is applied to <b>every</b> registered
 * context (command handlers as well as plain handlers like the API docs).
 */
public class TmmHttpServerTest extends BasicTest {
  private static final String API_KEY  = "s3cr3t-API-key";

  @Rule
  public TestName             testName = new TestName();

  private TmmHttpServer       httpServer;
  private int                 port;
  private String              testPath;
  private final ObjectMapper  mapper   = new ObjectMapper();

  /**
   * Starts the singleton {@link TmmHttpServer} on a free port without an API key and registers a capturing command context for the current test.
   *
   * @throws Exception
   *           thrown if the server cannot be started
   */
  @Before
  public void setup() throws Exception {
    super.setup();

    TmmHttpServer.init();
    httpServer = TmmHttpServer.getInstance();

    port = findFreePort();
    httpServer.updateConfiguration(true, port, "");

    // unique context path per test method - the server instance is reused across methods
    testPath = "test-" + testName.getMethodName().replaceAll("[^a-zA-Z0-9]", "");
  }

  /**
   * Stops the HTTP server again.
   *
   * @throws Exception
   *           thrown if the server cannot be stopped
   */
  @After
  public void tearDown() throws Exception {
    httpServer.updateConfiguration(false, port, "");
  }

  /**
   * Tests that a command context works without an API key configured and that the response is proper JSON.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void worksWithoutApiKeyConfigured() throws Exception {
    CapturingHandler handler = registerContext(new CapturingHandler());

    HttpResponse<String> response = post(testPath, "[]", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-type").orElse("")).startsWith("application/json");
    assertThat(messageOf(response)).isEqualTo("commands prepared");
    assertThat(handler.receivedActions).isEmpty(); // empty array -> no commands, but still processed
  }

  /**
   * Tests that the command context rejects requests without an API key when one is configured.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void commandContextRejectsMissingApiKey() throws Exception {
    CapturingHandler handler = registerContext(new CapturingHandler());
    httpServer.updateConfiguration(true, port, API_KEY);

    HttpResponse<String> response = post(testPath, "[{\"action\":\"update\"}]", null);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(messageOf(response)).isEqualTo("Invalid API key");
    assertThat(handler.receivedActions).isNull(); // handler was never called
  }

  /**
   * Tests that the command context rejects a wrong API key and accepts the correct one.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void commandContextChecksApiKey() throws Exception {
    CapturingHandler handler = registerContext(new CapturingHandler());
    httpServer.updateConfiguration(true, port, API_KEY);

    HttpResponse<String> wrongKey = post(testPath, "[{\"action\":\"update\"}]", "wrong-key");
    assertThat(wrongKey.statusCode()).isEqualTo(403);
    assertThat(handler.receivedActions).isNull();

    HttpResponse<String> rightKey = post(testPath, "[{\"action\":\"update\"}]", API_KEY);
    assertThat(rightKey.statusCode()).isEqualTo(200);
    assertThat(handler.receivedActions).containsExactly("update");
  }

  /**
   * Tests that removing the API key again disables the check.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void blankApiKeyDisablesTheCheck() throws Exception {
    registerContext(new CapturingHandler());
    httpServer.updateConfiguration(true, port, API_KEY);
    httpServer.updateConfiguration(true, port, "");

    HttpResponse<String> response = post(testPath, "[]", null);
    assertThat(response.statusCode()).isEqualTo(200);
  }

  /**
   * Tests that the API key check is also applied to plain handlers (the new {@code /api/docs} endpoint was the motivation for moving the check into
   * the shared context creation).
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void apiKeyCheckAppliesToAllContextsIncludingDocs() throws Exception {
    httpServer.updateConfiguration(true, port, API_KEY);

    HttpResponse<String> withoutKey = get("docs", null);
    assertThat(withoutKey.statusCode()).isEqualTo(403);
    assertThat(messageOf(withoutKey)).isEqualTo("Invalid API key");

    HttpResponse<String> withWrongKey = get("docs", "nope");
    assertThat(withWrongKey.statusCode()).isEqualTo(403);

    HttpResponse<String> withKey = get("docs", API_KEY);
    assertThat(withKey.statusCode()).isEqualTo(200);
    assertThat(withKey.body()).contains("\"openapi\"");
  }

  /**
   * Tests that a failing command handler is mapped to an HTTP 500 including its exception message.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void handlerExceptionBecomesServerError() throws Exception {
    CapturingHandler handler = registerContext(new CapturingHandler());
    handler.failWith = new RuntimeException("boom");

    HttpResponse<String> response = post(testPath, "[{\"action\":\"update\"}]", null);

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(messageOf(response)).isEqualTo("boom");
  }

  /**
   * Tests that the response code and message of the {@link TmmCommandResponse} are passed through to the caller.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void customResponseCodeIsPassedThrough() throws Exception {
    CapturingHandler handler = registerContext(new CapturingHandler());
    handler.responseCode = 400;
    handler.responseMessage = "nope";

    HttpResponse<String> response = post(testPath, "[{\"action\":\"update\"}]", null);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(messageOf(response)).isEqualTo("nope");
  }

  /**
   * Tests that a malformed JSON body is reported as an error instead of silently swallowed.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void malformedJsonBecomesServerError() throws Exception {
    registerContext(new CapturingHandler());

    HttpResponse<String> response = post(testPath, "{ not valid json", null);

    assertThat(response.statusCode()).isEqualTo(500);
  }

  private CapturingHandler registerContext(CapturingHandler handler) {
    httpServer.createContext(testPath, handler);
    return handler;
  }

  private String messageOf(HttpResponse<String> response) throws Exception {
    JsonNode node = mapper.readTree(response.body());
    return node.path("message").asText();
  }

  private HttpResponse<String> get(String context, String apiKey) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/api/" + context))
        .timeout(Duration.ofSeconds(10))
        .GET();
    applyApiKey(builder, apiKey);

    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String context, String body, String apiKey) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/api/" + context))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    applyApiKey(builder, apiKey);

    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void applyApiKey(HttpRequest.Builder builder, String apiKey) {
    if (apiKey != null) {
      builder.header("api-key", apiKey);
    }
  }

  private static int findFreePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /**
   * The class {@link CapturingHandler} stores the actions of all received commands for assertions.
   */
  private static class CapturingHandler extends AbstractCommandHandler {
    private List<String>     receivedActions;
    private RuntimeException failWith;
    private int              responseCode    = 200;
    private String           responseMessage = "commands prepared";

    @Override
    public TmmCommandResponse post(HttpExchange httpExchange) throws Exception {
      if (failWith != null) {
        // still need to drain the request body to keep the exchange sane
        httpExchange.getRequestBody().readAllBytes();
        throw failWith;
      }
      return super.post(httpExchange);
    }

    @Override
    protected TmmCommandResponse processCommands(List<Command> commands) {
      receivedActions = commands.stream().map(c -> c.action).toList();
      return new TmmCommandResponse(responseCode, responseMessage);
    }
  }
}
