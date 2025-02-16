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

package com.velocitypowered.proxy.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.util.SseClient;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages groups of servers and handles updates from an SSE endpoint.
 */
public class ServerGroup {
  private static final Logger logger = LogManager.getLogger(ServerGroup.class);
  private final VelocityServer velocityServer;
  private final ServerMap serverMap;
  private final Map<String, Set<RegisteredServer>> groupMap;
  private SseClient sseClient;

  /**
   * Initializes the ServerGroup with the given server map and SSE endpoint.
   *
   * @param serverMap the server map
   * @param velocityServer the server
   */
  public ServerGroup(VelocityServer velocityServer, ServerMap serverMap) {
    this.velocityServer = velocityServer;
    this.serverMap = serverMap;
    this.groupMap = new ConcurrentHashMap<>();
  }

  /**
   * Starts the SSE connection to the given URL.
   *
   * @param sseUrl the SSE endpoint URL
   */
  public void startSseConnection(String sseUrl) {
    sseClient = new SseClient(sseUrl, Collections.emptyMap(), this::handleSseEvent);
    sseClient.start();
  }

  /**
   * Stops the SSE connection.
   */
  public void stopSseConnection() {
    logger.info("Stopping SSE connection");
    if (sseClient != null) {
      sseClient.stop();
    }
  }

  /**
   * Updates the group with the given name with the specified server IPs.
   *
   * @param groupName the group name
   * @param serverIps the set of server IPs
   */
  public void updateGroup(String groupName, Set<String> serverIps, Boolean isForce) {
    logger.info("Updating group '{}' with servers: {}", groupName, serverIps);
    Set<RegisteredServer> currentServers = groupMap.getOrDefault(groupName, new HashSet<>());

    Set<String> newServers = serverIps.stream().map(ip -> String.format("%s-%d", groupName, ip.hashCode())).collect(Collectors.toSet());

    for (String ip : serverIps) {
      String serverName = String.format("%s-%d", groupName, ip.hashCode());
      if (currentServers.stream().noneMatch(server -> server.getServerInfo().getName().equals(serverName))) {
        ServerInfo serverInfo = new ServerInfo(serverName, new InetSocketAddress(ip, 25565));
        RegisteredServer registeredServer = serverMap.register(serverInfo);
        currentServers.add(registeredServer);
        if (isForce) {
          velocityServer.getConfiguration().getAttemptConnectionOrder().add(serverName);
        }
        logger.info("Registered new server '{}' with IP '{}'", serverName, ip);
      }
    }

    Iterator<RegisteredServer> iterator = currentServers.iterator();
    while (iterator.hasNext()) {
      RegisteredServer server = iterator.next();
      if (!newServers.contains(server.getServerInfo().getName())) {
        if (isForce) {
          velocityServer.getConfiguration().getAttemptConnectionOrder().remove(server.getServerInfo().getName());
        }
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

    Type type = new TypeToken<Map<String, Object>>(){}.getType();
    Map<String, Object> data = new Gson().fromJson(event, type);

    String groupName = (String) data.get("name");
    Set<String> serverIps = new HashSet<>((ArrayList<String>) data.get("serverIps"));
    Boolean isForce = (Boolean) data.get("isForce");
    logger.info("Parsed event data - groupName: '{}', serverIps: {}, isForce: {}", groupName, serverIps, isForce);
    updateGroup(groupName, serverIps, isForce);
  }
}