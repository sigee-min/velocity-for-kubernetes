# First stage: Build the application
FROM docker.io/library/openjdk:21-slim AS build
WORKDIR /app

COPY . .
RUN apt update -y
RUN apt install -y git
RUN ./gradlew build

# Second stage: Run the application
FROM docker.io/library/openjdk:21-slim
WORKDIR /app

COPY --from=build /app/build/libs/*-all.jar /app/velocity.jar
COPY --from=build /app/deploy/velocity.toml /app/velocity.toml

# Set default environment variables
ENV BIND="0.0.0.0:25577"
ENV MOTD="<#09add3>A Velocity Server"
ENV SHOW_MAX_PLAYERS=500
ENV ONLINE_MODE=true
ENV FORCE_KEY_AUTHENTICATION=true
ENV PREVENT_CLIENT_PROXY_CONNECTIONS=false
ENV PLAYER_INFO_FORWARDING_MODE="MODERN"
ENV FORWARDING_SECRET_FILE="forwarding.secret"
ENV ANNOUNCE_FORGE=false
ENV KICK_EXISTING_PLAYERS=false
ENV PING_PASSTHROUGH="DISABLED"
ENV ENABLE_PLAYER_ADDRESS_LOGGING=true
ENV COMPRESSION_THRESHOLD=256
ENV COMPRESSION_LEVEL=-1
ENV LOGIN_RATELIMIT=3000
ENV CONNECTION_TIMEOUT=5000
ENV READ_TIMEOUT=30000
ENV HAPROXY_PROTOCOL=false
ENV TCP_FAST_OPEN=false
ENV BUNGEE_PLUGIN_MESSAGE_CHANNEL=true
ENV SHOW_PING_REQUESTS=false
ENV FAILOVER_ON_UNEXPECTED_SERVER_DISCONNECT=true
ENV ANNOUNCE_PROXY_COMMANDS=true
ENV LOG_COMMAND_EXECUTIONS=false
ENV LOG_PLAYER_CONNECTIONS=true
ENV QUERY_ENABLED=false
ENV QUERY_PORT=25577
ENV QUERY_MAP="Velocity"
ENV SHOW_PLUGINS=false
ENV SSE_ENDPOINT="https://localhost:25575"
ENV JAVA_OPTS="-Xms2G -Xmx2G -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:MaxInlineLevel=15"

# Expose the port
EXPOSE 25565
EXPOSE 25577

# Replace placeholders in velocity.toml with environment variable values
CMD sed -i "s|{{BIND}}|${BIND}|g" /app/velocity.toml && \
    sed -i "s|{{MOTD}}|${MOTD}|g" /app/velocity.toml && \
    sed -i "s|{{SHOW_MAX_PLAYERS}}|${SHOW_MAX_PLAYERS}|g" /app/velocity.toml && \
    sed -i "s|{{ONLINE_MODE}}|${ONLINE_MODE}|g" /app/velocity.toml && \
    sed -i "s|{{FORCE_KEY_AUTHENTICATION}}|${FORCE_KEY_AUTHENTICATION}|g" /app/velocity.toml && \
    sed -i "s|{{PREVENT_CLIENT_PROXY_CONNECTIONS}}|${PREVENT_CLIENT_PROXY_CONNECTIONS}|g" /app/velocity.toml && \
    sed -i "s|{{PLAYER_INFO_FORWARDING_MODE}}|${PLAYER_INFO_FORWARDING_MODE}|g" /app/velocity.toml && \
    sed -i "s|{{FORWARDING_SECRET_FILE}}|${FORWARDING_SECRET_FILE}|g" /app/velocity.toml && \
    sed -i "s|{{ANNOUNCE_FORGE}}|${ANNOUNCE_FORGE}|g" /app/velocity.toml && \
    sed -i "s|{{KICK_EXISTING_PLAYERS}}|${KICK_EXISTING_PLAYERS}|g" /app/velocity.toml && \
    sed -i "s|{{PING_PASSTHROUGH}}|${PING_PASSTHROUGH}|g" /app/velocity.toml && \
    sed -i "s|{{ENABLE_PLAYER_ADDRESS_LOGGING}}|${ENABLE_PLAYER_ADDRESS_LOGGING}|g" /app/velocity.toml && \
    sed -i "s|{{COMPRESSION_THRESHOLD}}|${COMPRESSION_THRESHOLD}|g" /app/velocity.toml && \
    sed -i "s|{{COMPRESSION_LEVEL}}|${COMPRESSION_LEVEL}|g" /app/velocity.toml && \
    sed -i "s|{{LOGIN_RATELIMIT}}|${LOGIN_RATELIMIT}|g" /app/velocity.toml && \
    sed -i "s|{{CONNECTION_TIMEOUT}}|${CONNECTION_TIMEOUT}|g" /app/velocity.toml && \
    sed -i "s|{{READ_TIMEOUT}}|${READ_TIMEOUT}|g" /app/velocity.toml && \
    sed -i "s|{{HAPROXY_PROTOCOL}}|${HAPROXY_PROTOCOL}|g" /app/velocity.toml && \
    sed -i "s|{{TCP_FAST_OPEN}}|${TCP_FAST_OPEN}|g" /app/velocity.toml && \
    sed -i "s|{{BUNGEE_PLUGIN_MESSAGE_CHANNEL}}|${BUNGEE_PLUGIN_MESSAGE_CHANNEL}|g" /app/velocity.toml && \
    sed -i "s|{{SHOW_PING_REQUESTS}}|${SHOW_PING_REQUESTS}|g" /app/velocity.toml && \
    sed -i "s|{{FAILOVER_ON_UNEXPECTED_SERVER_DISCONNECT}}|${FAILOVER_ON_UNEXPECTED_SERVER_DISCONNECT}|g" /app/velocity.toml && \
    sed -i "s|{{ANNOUNCE_PROXY_COMMANDS}}|${ANNOUNCE_PROXY_COMMANDS}|g" /app/velocity.toml && \
    sed -i "s|{{LOG_COMMAND_EXECUTIONS}}|${LOG_COMMAND_EXECUTIONS}|g" /app/velocity.toml && \
    sed -i "s|{{LOG_PLAYER_CONNECTIONS}}|${LOG_PLAYER_CONNECTIONS}|g" /app/velocity.toml && \
    sed -i "s|{{QUERY_ENABLED}}|${QUERY_ENABLED}|g" /app/velocity.toml && \
    sed -i "s|{{QUERY_PORT}}|${QUERY_PORT}|g" /app/velocity.toml && \
    sed -i "s|{{QUERY_MAP}}|${QUERY_MAP}|g" /app/velocity.toml && \
    sed -i "s|{{SHOW_PLUGINS}}|${SHOW_PLUGINS}|g" /app/velocity.toml && \
    sed -i "s|{{SSE_ENDPOINT}}|${SSE_ENDPOINT}|g" /app/velocity.toml && \
    java $JAVA_OPTS -jar /app/velocity.jar
