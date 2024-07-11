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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.proxy.util.event.EventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SseClientTest {

  private static final Logger logger = LogManager.getLogger(SseClientTest.class);
  @Mock
  private EventHandler eventHandler;
  private HttpServer server;
  private SseClient sseClient;

  @BeforeEach
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);

    server = HttpServer.create(new InetSocketAddress(0), 0);
    String SSE_ENDPOINT = "/events";
    server.createContext(SSE_ENDPOINT, exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.getResponseHeaders().add("Connection", "keep-alive");
      exchange.getResponseHeaders().add("Transfer-Encoding", "chunked");
      exchange.getResponseHeaders().add("X-Powered-By", "Native Application Server");
      exchange.sendResponseHeaders(200, 0);
      OutputStream output = exchange.getResponseBody();
      for (int i = 0; i < 3; i++) {
        String event = "data: {\"name\":\"group" + "\",\"serverIps\":[\"192.168.0." + (i + 1) + "\",\"192.168.0." + (i + 2) + "\"]}\n";
        output.write(event.getBytes());
        output.flush();
        sleepQuitely(500);
      }
      output.close();
    });
    server.start();

    logger.info("starting server");
    String url = "http://localhost:" + server.getAddress().getPort() + SSE_ENDPOINT;
    sseClient = new SseClient(url, new HashMap<>(), eventHandler);
  }

  @AfterEach
  public void tearDown() {
    if (sseClient != null) {
      sseClient.stop();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  public void testSseConnectionAndDataReception() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);  // 3개의 이벤트 예상

    doAnswer(invocation -> {
      String event = invocation.getArgument(0);
      assertTrue(event.contains("\"name\":\"group"));
      assertTrue(event.contains("\"serverIps\":"));
      latch.countDown();
      return null;
    }).when(eventHandler).handle(anyString());

    sseClient.start();
    boolean allEventsReceived = latch.await(10, TimeUnit.SECONDS);

    assertTrue(allEventsReceived, "모든 예상 이벤트를 받지 못했습니다");
    verify(eventHandler, times(3)).handle(anyString());
  }

  private void sleepQuitely(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.error("Error sleeping: " + e.getMessage());
    }
  }
}