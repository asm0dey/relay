# Relay

Relay is a self-hosted tunneling service that exposes local HTTP and WebSocket applications to the internet through a gRPC channel. It lets you make services running on your machine accessible via a public domain — without relying on a SaaS provider, without rate limits, and without sharing your traffic with a third party.

**Key differentiators over tools like ngrok:**

- Fully self-hosted — you control the server, the domain, and the data
- No SaaS dependency — works on your own infrastructure
- No rate limits or connection caps
- Supports HTTP, WebSocket, and large-file streaming out of the box

## Architecture

```
External Client
      |
      v
   Nginx (reverse proxy)
      |
      |-- HTTP requests --> proxy_pass --> Relay Server (:8080)
      |                                        |
      |-- WebSocket -----> proxy_pass --------/
      |   (upgrade)                            |
      |                             gRPC bidirectional stream
      |-- gRPC ----------> grpc_pass ---------/
                                               |
                                               v
                                        Client Agent
                                               |
                                               v
                                     Local Application
```

**How it works:** The Relay server runs on a publicly accessible host behind Nginx. A client agent runs alongside your local application and connects to the server over a persistent gRPC stream. When an external HTTP or WebSocket request arrives at the server, it is forwarded through the gRPC tunnel to the client agent, which proxies it to your local app. The response flows back the same way.

## Build and Run

### Prerequisites

- **JDK 25** or later
- **Maven 3.9+** (or use the included Maven wrapper `./mvnw`)

### Build

The project is a multi-module Maven build with two modules: `server` and `client`.

Build everything from the repository root:

```shell
./mvnw package
```

### Run the Server

```shell
java -jar server/target/quarkus-app/quarkus-run.jar
```

The server starts on port **8080** by default.

### Run the Client

```shell
java -jar client/target/quarkus-app/quarkus-run.jar
```

The client connects to the server and registers your local application for tunneling.

## Deploy with Nginx

The Relay server serves HTTP, WebSocket, and gRPC traffic on a single port. Nginx routes each traffic type to the correct handler using separate `location` blocks.

### DNS

Set up a wildcard DNS record pointing to your Nginx host:

```
*.tunnel.example.com  A  <your-server-ip>
```

Each tunneled application gets its own subdomain (e.g., `myapp.tunnel.example.com`).

### Nginx Configuration

```nginx
# Required for WebSocket upgrade header forwarding
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80;
    listen 443 ssl http2;
    server_name *.tunnel.example.com;

    # TLS configuration (adjust paths to your certificates)
    # ssl_certificate     /etc/nginx/ssl/tunnel.example.com.crt;
    # ssl_certificate_key /etc/nginx/ssl/tunnel.example.com.key;

    # --- gRPC tunnel (HTTP/2) ---
    location /tunnel.TunnelService/ {
        grpc_pass grpc://localhost:8080;

        # Long-lived bidirectional stream — use generous timeouts
        grpc_read_timeout 30m;
        grpc_send_timeout 30m;
        grpc_socket_keepalive on;
    }

    # --- WebSocket upgrade ---
    location /ws-upgrade/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }

    # --- HTTP (default) ---
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Common Pitfalls

**gRPC fails with connection errors:**
The gRPC tunnel requires HTTP/2. Make sure `http2` is enabled on the `listen` directive (`listen 443 ssl http2;`). Without it, Nginx falls back to HTTP/1.1 and the gRPC stream cannot be established.

**WebSocket connections return a plain HTTP response:**
The `Upgrade` header is hop-by-hop and Nginx does not forward it by default. You must include the `map` block and set `proxy_set_header Upgrade` and `proxy_set_header Connection` in the WebSocket location. Without these, the upstream never sees the upgrade request.

**Wildcard subdomain DNS:**
Each tunneled application is identified by its subdomain. You need a wildcard DNS record (`*.tunnel.example.com`) pointing to your Nginx host, and a matching `server_name` in Nginx. Without this, only the bare domain will resolve.

## Configuration Reference

### Server

Configuration is in `server/src/main/resources/application.yml`.

| Property | Default | Description |
|----------|---------|-------------|
| `relay.domain` | `tunnel.example.com` | Base domain for tunnel subdomains |
| `relay.allowed-secret-keys` | `[]` | List of allowed client authentication keys |
| `relay.websocket.max-tunnels-per-domain` | `100` | Maximum concurrent WebSocket tunnels per domain |
| `relay.websocket.upgrade-timeout` | `30s` | Timeout for WebSocket upgrade handshake |
| `quarkus.grpc.server.max-inbound-message-size` | `5242880` (5 MB) | Maximum inbound gRPC message size |
| `quarkus.grpc.server.keep-alive-time` | `60s` | gRPC keep-alive interval |
| `quarkus.http.port` | `8080` | HTTP listen port |
| `quarkus.websockets-next.server.auto-ping-interval` | `30s` | WebSocket keep-alive ping interval |
