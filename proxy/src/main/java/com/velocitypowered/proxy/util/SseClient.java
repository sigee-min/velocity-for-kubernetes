/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

import com.velocitypowered.proxy.util.event.EventHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SSEClient is a class that allows establishing and managing a Server-Sent Events (SSE) connection.
 */
public class SseClient {
  private static final Logger logger = LogManager.getLogger(SseClient.class);
  private static final long DEFAULT_RECONNECT_SAMPLING_TIME_MILLIS = 5L * 1000L;
  private final String url;
  private final Map<String, String> headerParams;
  private final EventHandler eventHandler;
  private final AtomicBoolean shouldRun = new AtomicBoolean(true);
  private final AtomicBoolean isConnecting = new AtomicBoolean(false);
  private final HttpClient client;
  private final ScheduledExecutorService reconnectScheduler = Executors.newScheduledThreadPool(1);

  /**
   * Constructs a new SSEClient instance.
   *
   * @param url The URL to connect to for SSE.
   * @param headerParams Headers to include in the SSE request.
   * @param eventHandler Handler for processing received events.
   */
  public SseClient(String url, Map<String, String> headerParams, EventHandler eventHandler) {
    this.url = url;
    this.headerParams = headerParams;
    this.eventHandler = eventHandler;
    this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();
    logger.debug("SseClient initialized with URL: {} and headers: {}", url, headerParams);
  }

  /**
   * Starts the SSE connection.
   * This method initializes and starts the SSE connection by executing the connect() method.
   */
  public void start() {
    logger.info("Starting SSE connection to URL: {}", url);
    connect();
  }

  /**
   * Establishes a connection to the SSE endpoint.
   * Sends a GET request to the provided URL with necessary headers to receive server-sent events.
   * Handles the response and takes appropriate action based on the response status code.
   * If the connection fails, a reconnection is scheduled.
   */
  private void connect() {
    if (!shouldRun.get() || isConnecting.getAndSet(true)) {
      logger.info("Connection attempt skipped as another connection is in progress or shutdown initiated.");
      return;
    }

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(2))
            .GET();
    headerParams.forEach(requestBuilder::header);

    HttpRequest request = requestBuilder.build();
    client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenAccept(response -> {
              if (response.statusCode() == 200) {
                handleResponse(response.body());
              } else {
                scheduleReconnect();
              }
            })
            .exceptionally(ex -> {
              logger.error("Exception while connecting to SSE endpoint: ", ex);
              scheduleReconnect();
              return null;
            })
            .whenComplete((unused, throwable) -> isConnecting.set(false));
  }

  /**
   * Handles the response from the input stream.
   *
   * @param inputStream the input stream to read the response from
   */
  private void handleResponse(InputStream inputStream) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      StringBuilder eventBuilder = new StringBuilder();
      while ((line = reader.readLine()) != null && shouldRun.get()) {
        if (line.startsWith("data:")) {
          eventBuilder.append(line.substring(5).trim()).append("\n");
        }
        if (line.isEmpty() && !eventBuilder.isEmpty()) {
          eventHandler.handle(eventBuilder.toString().trim());
          eventBuilder.setLength(0);
        }
      }
    } catch (IOException e) {
      logger.error("Error reading SSE response: ", e);
      scheduleReconnect();
    }
  }

  /**
   * Schedules a reconnect task to be executed by the reconnect scheduler.
   * If the connectivity manager should not run, the method returns without scheduling the task.
   * The task will call the connect() method after the default reconnect sampling time has passed.
   */
  private void scheduleReconnect() {
    if (!shouldRun.get() || isConnecting.get()) {
      logger.info("Reconnection attempt aborted due to shutdown or ongoing connection.");
      return;
    }
    logger.info("Scheduling reconnect in {} milliseconds.", DEFAULT_RECONNECT_SAMPLING_TIME_MILLIS);
    reconnectScheduler.schedule(this::connect, DEFAULT_RECONNECT_SAMPLING_TIME_MILLIS, TimeUnit.MILLISECONDS);
  }

  /**
   * Stops the SSE connection.
   * This method sets shouldRun to false, preventing further reconnection attempts, and shuts down the reconnect scheduler.
   */
  public void stop() {
    shouldRun.set(false);
    reconnectScheduler.shutdownNow();
    isConnecting.set(false);
    logger.info("SSE connection stopped and reconnect scheduler shutdown.");
  }
}
