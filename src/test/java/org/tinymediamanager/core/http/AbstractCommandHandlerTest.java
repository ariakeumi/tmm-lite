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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The class {@link AbstractCommandHandlerTest} verifies the JSON payload parsing of the {@link AbstractCommandHandler} (command array, single command
 * object and broken payloads).
 */
public class AbstractCommandHandlerTest {
  private HttpServer             server;
  private String                 baseUrl;
  private final CapturingHandler capturingHandler = new CapturingHandler();

  /**
   * Starts a plain HTTP server with a capturing command handler.
   *
   * @throws Exception
   *           thrown if the server cannot be started
   */
  @Before
  public void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/command", exchange -> {
      TmmCommandResponse commandResponse;
      try {
        commandResponse = capturingHandler.post(exchange);
      }
      catch (Exception e) {
        commandResponse = new TmmCommandResponse(500, e.getMessage());
      }

      byte[] body = ("{\"message\":\"" + commandResponse.getResponseMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(commandResponse.getResponseCode(), body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
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
   * Tests that a JSON array of commands is deserialized into multiple {@link AbstractCommandHandler.Command}s.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void parsesArrayOfCommands() throws Exception {
    String payload = """
        [
          { "action": "update", "scope": { "name": "all" } },
          { "action": "scrape", "scope": { "name": "unscraped" } },
          { "action": "rename", "scope": { "name": "path", "args": ["/foo", "/bar" ] }, "args": { "profile": "myProfile" } }
        ]
        """;

    HttpResponse<String> response = post(payload);
    assertThat(response.statusCode()).isEqualTo(200);

    assertThat(capturingHandler.commands).hasSize(3);
    assertThat(capturingHandler.commands.get(0).action).isEqualTo("update");
    assertThat(capturingHandler.commands.get(0).scope.name).isEqualTo("all");
    assertThat(capturingHandler.commands.get(1).action).isEqualTo("scrape");
    assertThat(capturingHandler.commands.get(1).scope.name).isEqualTo("unscraped");
    assertThat(capturingHandler.commands.get(2).action).isEqualTo("rename");
    assertThat(capturingHandler.commands.get(2).scope.args).containsExactly("/foo", "/bar");
    assertThat(capturingHandler.commands.get(2).args).containsEntry("profile", "myProfile");
  }

  /**
   * Tests that a single command object (no array) is also accepted.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void parsesSingleCommandObject() throws Exception {
    HttpResponse<String> response = post("{ \"action\": \"writeNfo\" }");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(capturingHandler.commands).hasSize(1);
    assertThat(capturingHandler.commands.get(0).action).isEqualTo("writeNfo");
    // scope/args have sensible defaults
    assertThat(capturingHandler.commands.get(0).scope).isNotNull();
    assertThat(capturingHandler.commands.get(0).args).isEmpty();
  }

  /**
   * Tests that the scope name defaults to null (so the tasks can apply their own defaults) and unknown properties in the payload are silently
   * ignored.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void defaultsAndUnknownProperties() throws Exception {
    HttpResponse<String> response = post("{ \"action\": \"scrape\", \"unknownProperty\": 42 }");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(capturingHandler.commands).hasSize(1);
    assertThat(capturingHandler.commands.get(0).scope.name).isNull();
  }

  /**
   * Tests that a broken JSON payload is reported as an error to the caller.
   *
   * @throws Exception
   *           thrown on HTTP errors
   */
  @Test
  public void brokenJsonDoesNotProduceCommands() throws Exception {
    HttpResponse<String> response = post("{ not valid json");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(capturingHandler.commands).isEmpty();
  }

  private HttpResponse<String> post(String body) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/command"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * The class {@link CapturingHandler} stores the parsed commands for assertions instead of queueing real tasks.
   */
  private static class CapturingHandler extends AbstractCommandHandler {
    private final List<Command> commands = new ArrayList<>();

    @Override
    protected TmmCommandResponse processCommands(List<Command> commands) {
      this.commands.clear();
      this.commands.addAll(commands);
      return new TmmCommandResponse(200, "commands prepared");
    }
  }
}
