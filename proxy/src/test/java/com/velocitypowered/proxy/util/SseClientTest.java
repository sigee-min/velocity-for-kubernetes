/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.util;

import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.proxy.util.event.EventHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SseClientTest is a class for testing the SseClient class.
 */
public class SseClientTest {

  @Mock
  private EventHandler eventHandler;
  private HttpServer server;
  private SseClient sseClient;

  /**
   * Sets up the necessary resources and configurations for testing the SSE connection and data reception.
   * This method is executed before each test case.
   *
   * @throws IOException if an I/O error occurs while setting up the resources
   */
  @BeforeEach
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);

    server = HttpServer.create(new InetSocketAddress(0), 0);
    String sseEndpoint = "/events";
    server.createContext(sseEndpoint, exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.getResponseHeaders().add("Connection", "keep-alive");
      exchange.getResponseHeaders().add("Transfer-Encoding", "chunked");
      exchange.getResponseHeaders().add("X-Powered-By", "Native Application Server");
      exchange.sendResponseHeaders(200, 0);
      OutputStream output = exchange.getResponseBody();
      for (int i = 0; i < 3; i++) {
        String event = "data: {\"name\":\"group" + "\",\"serverIps\":[\"192.168.0." + (i + 1) + "\",\"192.168.0." + (i + 2) + "\"]}\n\n";
        output.write(event.getBytes());
        output.flush();
        sleepQuitely(1000);
      }
      output.close();
    });
    server.start();

    String url = "http://localhost:" + server.getAddress().getPort() + sseEndpoint;
    sseClient = new SseClient(url, new HashMap<>(), eventHandler);
  }

  /**
   * The tearDown method is used to clean up the resources used by the test.
   * It is typically called after each test case, ensuring that the resources are properly released even if the test fails.
   * In this particular case, the tearDown method checks if the SSE
   * client and the server are not null and then stops the SSE client and shuts down the server.
   * If the SSE client is not null, the stop() method is called on it to stop the SSE connection.
   * If the server is not null, the stop() method is called on it with a shutdown delay of 0, indicating an immediate shutdown.
   */
  @AfterEach
  public void tearDown() {
    if (sseClient != null) {
      sseClient.stop();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  /**
   * Sleeps for the specified number of milliseconds quietly.
   *
   * @param millis the number of milliseconds to sleep
   */
  private void sleepQuitely(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException err) {
      System.err.println(err.getMessage());
    }
  }
}