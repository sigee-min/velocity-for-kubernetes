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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * This class tests the ServerGroup class, especially the `updateGroup` method.
 * This method updates the group with the given name with the specified server IPs.
 */
class ServerGroupTest {

  @Test
  void testUpdateGroupWhenAllNewServers() {

    // Mocking necessary classes
    VelocityServer velocityServer = mock(VelocityServer.class);
    VelocityConfiguration velocityConfiguration = mock(VelocityConfiguration.class);
    ServerMap serverMap = mock(ServerMap.class);
    RegisteredServer registeredServer = mock(RegisteredServer.class);
    ServerInfo serverInfo = mock(ServerInfo.class);

    // Creating necessary objects
    when(serverMap.register(any(ServerInfo.class))).thenReturn(registeredServer);
    when(registeredServer.getServerInfo()).thenReturn(serverInfo);
    when(serverInfo.getName()).thenReturn("minecraft-server");
    when(velocityServer.getConfiguration()).thenReturn(velocityConfiguration);
    when(velocityServer.getConfiguration().getAttemptConnectionOrder()).thenReturn(new ArrayList<>());
    ServerGroup serverGroup = new ServerGroup(velocityServer, serverMap);

    // Parse test data
    String jsonData = "{\"name\":\"minecraft-server\",\"serverIps\":[\"10.244.0.7\",\"10.244.0.21\"]}";

    Type type = new TypeToken<Map<String, Object>>(){}.getType();
    Map<String, Object> data = new Gson().fromJson(jsonData, type);

    String groupName = (String) data.get("name");
    Set<String> serverIps = new HashSet<>((ArrayList<String>) data.get("serverIps"));

    // Perform the operation to update the group
    serverGroup.updateGroup(groupName, serverIps, true);

    // Verifying the interactions
    verify(serverMap, times(2)).register(any(ServerInfo.class));
  }

}