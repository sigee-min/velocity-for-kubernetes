/*
 * Copyright (C) 2024 Velocity Contributors
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

package com.velocitypowered.proxy.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages groups of servers and handles updates from an SSE endpoint.
 */
public class ServerGroup {
  private static final Logger logger = LogManager.getLogger(ServerGroup.class);

  private final ServerMap serverMap;
  private final Map<String, Set<RegisteredServer>> groupMap;
  private final HttpClient client = HttpClient.newHttpClient();
  private final Duration retryInterval = Duration.ofSeconds(5);

  /**
   * Initializes the ServerGroup with the given server map and SSE endpoint.
   *
   * @param serverMap   the server map
   */
  public ServerGroup(ServerMap serverMap) {
    this.serverMap = serverMap;
    this.groupMap = new ConcurrentHashMap<>();

  }

  /**
   * Starts the SSE connection to the given URL.
   *
   * @param sseUrl the SSE endpoint URL
   */
  public void startSseConnection(String sseUrl) {
    logger.info("Starting SSE connection to URL: {}", sseUrl);

    final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(sseUrl)).build();
    CompletableFuture<Void> connectionFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
      .thenAccept(response -> {
        logger.info("Connected to SSE endpoint. Processing events...");
        processEvents(response, sseUrl);
      }).exceptionally(ex -> {
        logger.error("Failed to connect to SSE endpoint: ", ex);
        scheduleReconnect(sseUrl);
        return null;
      });
  }

  /**
   * Processes the events received in the SSE response and handles them accordingly.
   *
   * @param response the SSE response containing the events
   * @param sseUrl the SSE endpoint URL
   */
  private void processEvents(HttpResponse<Stream<String>> response, String sseUrl) {
    try (var lines = response.body()) {
      StringBuilder eventBuilder = new StringBuilder();
      lines.forEach(line -> {
        if (line.startsWith("event:")) {
          eventBuilder.setLength(0);
          eventBuilder.append(line);
          logger.debug("Received event: {}", line);
        } else if (line.startsWith("data:")) {
          eventBuilder.append("\n").append(line);
          logger.debug("Received data: {}", line);
          handleSseEvent(eventBuilder.toString());
        }
      });
    } catch (Exception e) {
      logger.error("Error processing SSE events: ", e);
      startSseConnection(sseUrl); // Try reconnecting if stream processing fails
    }
  }

  /**
   * Schedules a reconnect to the given SSE URL after a specified interval.
   *
   * @param sseUrl the SSE endpoint URL to reconnect to
   */
  private void scheduleReconnect(String sseUrl) {
    logger.info("Scheduling reconnect in {} seconds", retryInterval.getSeconds());
    try {
      TimeUnit.SECONDS.sleep(retryInterval.getSeconds());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    startSseConnection(sseUrl);
  }

  /**
   * Updates the group with the given name with the specified server IPs.
   *
   * @param groupName the group name
   * @param serverIps the set of server IPs
   */
  public void updateGroup(String groupName, Set<String> serverIps) {
    logger.info("Updating group '{}' with servers: {}", groupName, serverIps);
    Set<RegisteredServer> currentServers = groupMap.getOrDefault(groupName, new HashSet<>());

    // HashSet of new servers
    Set<String> newServers = serverIps.stream().map(ip -> String.format("%s-%d", groupName, ip.hashCode())).collect(Collectors.toSet());

    // Add new servers
    for (String ip : serverIps) {
      String serverName = String.format("%s-%d", groupName, ip.hashCode());
      if (currentServers.stream().noneMatch(server -> server.getServerInfo().getName().equals(serverName))) {
        ServerInfo serverInfo = new ServerInfo(serverName, new InetSocketAddress(ip, 25565));
        RegisteredServer registeredServer = serverMap.register(serverInfo);
        currentServers.add(registeredServer);
        logger.info("Registered new server '{}' with IP '{}'", serverName, ip);
      }
    }

    // Remove old servers
    Iterator<RegisteredServer> iterator = currentServers.iterator();
    while (iterator.hasNext()) {
      RegisteredServer server = iterator.next();
      if (!newServers.contains(server.getServerInfo().getName())) {
        serverMap.unregister(server.getServerInfo());
        iterator.remove();
        logger.info("Unregistered old server '{}'", server.getServerInfo().getName());
      }
    }

    groupMap.put(groupName, currentServers);
    logger.info("Group '{}' updated with {} servers", groupName, currentServers.size());
  }

  /**
   * Retrieves the servers for the specified group.
   *
   * @param groupName the group name
   * @return the set of servers for the group
   */
  public Set<RegisteredServer> getServersForGroup(String groupName) {
    Set<RegisteredServer> servers = groupMap.getOrDefault(groupName, Collections.emptySet());
    logger.info("Retrieved {} servers for group '{}'", servers.size(), groupName);
    return servers;
  }

  /**
   * Handles an SSE event.
   *
   * @param event the SSE event
   */
  public void handleSseEvent(String event) {
    logger.info("Handling SSE event: {}", event);
    if (event.startsWith("event:minecraftservergroup")) {
      int dataIndex = event.indexOf("data:") + 5;
      String dataLine = event.substring(dataIndex).trim();
      JsonObject jsonObject = JsonParser.parseString(dataLine).getAsJsonObject();
      String groupName = jsonObject.get("name").getAsString();
      Set<String> serverIps = new Gson().fromJson(jsonObject.get("serverIps"), Set.class);
      logger.info("Parsed event data - groupName: '{}', serverIps: {}", groupName, serverIps);
      updateGroup(groupName, serverIps);
    } else {
      logger.warn("Received unhandled event type: {}", event);
    }
  }
}
