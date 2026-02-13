# Feature Specification: Tunnel Client CLI Ergonomics

**Feature Branch**: `[002-tunnel-client-cli]`
**Created**: 2026-02-13
**Status**: Draft
**Input**: User description: "I need the following ergonomics for client: client 3000 -s tun.asm0dey.site -d xxx -k secret-key where 1) 3000 - port of a local http application 2) tun.asm0dey.site - server to which I want to connect 3) -d xxx — optional subdomain, will be random is not defined 4) secret-key — obviously the secret key"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Connect Local Service with Minimal Arguments (Priority: P1)

As a developer, I want to expose my local HTTP service running on a specific port to the internet with minimal typing, so that I can quickly share my work without configuring full URLs.

**Why this priority**: This is the core value proposition - reducing friction for the most common use case where users just want to expose a local port. The positional port argument is significantly faster than typing a full URL.

**Independent Test**: Can be fully tested by running the client with just a port number, server URL, and secret key. The tunnel should establish successfully and external requests to the assigned subdomain should reach the local service.

**Acceptance Scenarios**:

1. **Given** a local HTTP service running on port 3000, **When** the user runs `client 3000 -s tun.example.com -k my-secret`, **Then** the client connects to the server and displays the assigned public URL (e.g., `https://abc123.tun.example.com`).

2. **Given** a local HTTP service on port 8080, **When** the user runs `client 8080 -s tun.example.com -d myapp -k secret`, **Then** the client connects and the public URL uses the requested subdomain (`https://myapp.tun.example.com`).

3. **Given** the user runs `client 3000 -s tun.example.com -k secret` without specifying `-d`, **Then** the server assigns a random unique subdomain and the client displays it clearly in the output.

---

### User Story 2 - Clear Help and Usage Information (Priority: P2)

As a developer, I want to see clear help text when I run the client with `-h` or with invalid arguments, so that I can understand the command syntax without reading external documentation.

**Why this priority**: Good error messages and help text reduce user confusion and support burden. This enhances usability but doesn't block core functionality.

**Independent Test**: Can be tested independently by running `--help` or providing invalid arguments and verifying the output explains the syntax correctly, including examples.

**Acceptance Scenarios**:

1. **Given** the user runs `client --help` or `client -h`, **Then** the output displays usage information showing: the positional port argument, the `-s` flag for server URL, the optional `-d` flag for subdomain, and the `-k` flag for secret key with clear examples.

2. **Given** the user runs `client` without any arguments, **Then** the client displays a helpful error message explaining that a port number is required, along with a brief usage example.

3. **Given** the user runs `client 3000` without providing `-s` or `-k`, **Then** the client displays a specific error indicating which required parameters are missing.

---

### User Story 3 - Connection Feedback and Status Display (Priority: P3)

As a developer, I want clear feedback about my connection status including the public URL and any connection issues, so that I know when my tunnel is ready and can troubleshoot problems.

**Why this priority**: While not essential for basic functionality, clear status messages significantly improve the user experience, especially for first-time users verifying their tunnel works.

**Independent Test**: Can be tested by running the client and verifying the output shows: connection attempt, successful connection confirmation, assigned public URL, and clear error messages if connection fails.

**Acceptance Scenarios**:

1. **Given** a successful connection, **When** the client establishes the tunnel, **Then** it displays a clear message showing the public URL (e.g., `Tunnel ready: https://abc123.tun.example.com -> localhost:3000`).

2. **Given** the server is unreachable, **When** the client attempts to connect, **Then** it displays a clear, actionable error message (e.g., `Failed to connect to tun.example.com: Connection refused`).

3. **Given** the secret key is invalid, **When** the client attempts authentication, **Then** it displays an authentication failure message and exits with a non-zero status code.

---

### Edge Cases

- What happens when the specified local port is not accepting connections? The client should detect this early and provide a helpful error message.
- How does the system handle an invalid port number (e.g., negative, > 65535, or non-numeric)? The client should validate the port and show a clear error.
- What happens when the requested subdomain (`-d`) is already in use by another client? The server should reject it with a conflict response, and the client MUST display the error message and exit with code 2.
- How does the client behave when the server URL is malformed? Specific cases: (a) missing hostname entirely -> error with exit code 1, (b) invalid characters in hostname -> error with exit code 1, (c) hostname with protocol prefix (http://, wss://) -> strip protocol and warn user, (d) empty or whitespace-only hostname -> error with exit code 1.
- What happens when arguments contain only whitespace? The client MUST treat whitespace-only arguments as empty/missing and show validation errors.
- What happens on network interruption during an active session? The client should attempt to reconnect automatically and display status updates.
- What exit codes does the client use? The client MUST use specific exit codes: 0 (success), 1 (invalid arguments or configuration), 2 (connection failed), 3 (authentication failed), 130 (interrupted/SIGINT).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The client MUST accept a positional argument for the local HTTP service port number (e.g., `3000`).
- **FR-002**: The client MUST accept a `-s` (or `--server`) flag followed by the tunnel server hostname (e.g., `tun.asm0dey.site`).
- **FR-003**: The client MUST accept a `-k` (or `--key`) flag followed by the authentication secret key.
- **FR-004**: The client MUST accept an optional `-d` (or `--subdomain`) flag for requesting a specific subdomain; if omitted, the server assigns a random subdomain.
- **FR-005**: The client MUST construct the full local URL from the port number using `http://localhost:{port}` as the default.
- **FR-006**: The client MUST construct the full server WebSocket URL from the hostname using `wss://{hostname}/ws` as the default (secure WebSocket).
- **FR-007**: The client MUST validate that the port is a valid integer between 1 and 65535.
- **FR-008**: The client MUST display the assigned public URL upon successful connection.
- **FR-009**: The client MUST provide clear error messages when required arguments are missing.
- **FR-010**: The client MUST support a `-h` or `--help` flag that displays usage information and exits with status 0.
- **FR-011**: The client MUST use Quarkus Picocli extension for all CLI argument parsing and command handling.
- **FR-012**: The client MUST support a properties file for default configuration values, with CLI arguments and environment variables taking precedence.
- **FR-013**: The client MUST support an `--insecure` flag that uses `ws://` instead of `wss://` for local development and testing.
- **FR-014**: The client MUST validate that requested subdomains conform to DNS label rules: lowercase alphanumeric characters and hyphens, 1-63 characters, starting and ending with alphanumeric.
- **FR-015**: The client MUST support `--quiet` flag (errors only) and `--verbose` flag (debug logging) for output control. Verbosity mapping: `--quiet` = ERROR level, normal = INFO level, `--verbose` = DEBUG level.
- **FR-016**: The client MUST validate server hostnames to ensure they are valid DNS names: contain only alphanumeric characters, dots, and hyphens; no leading/trailing dots or hyphens; labels between dots follow DNS rules.
- **FR-017**: The client MUST support properties files in the following locations (searched in order): (1) `./application.properties` (project directory), (2) `~/.relay/config.properties` (user home), (3) `/etc/relay/config.properties` (system-wide). CLI arguments override all file-based configuration.
- **FR-018**: Help output MUST follow this format: Usage line, blank line, positional arguments section, blank line, options section with aligned descriptions, blank line, examples section with 2-3 common use cases.

### Key Entities

- **Port**: The local service port number (integer, 1-65535). Represents the entry point of the user's local HTTP application.
- **Server Hostname**: The tunnel server's domain name without protocol or path (e.g., `tun.example.com`). Used to construct the WebSocket connection URL. The client accepts both `ws://` and `wss://` protocols - `wss://` (secure) is the default, `ws://` (insecure) is used only when `--insecure` flag is provided.
- **Secret Key**: Authentication credential for establishing the tunnel. Pre-registered with the server.
- **Subdomain**: The unique identifier prepended to the server hostname to form the public URL (e.g., `abc123` in `abc123.tun.example.com`). Optional when requesting, always present in the final URL.
- **Local URL**: The full URL constructed from the port (e.g., `http://localhost:3000`) used to forward incoming requests to the local service.
- **Server URL**: The full WebSocket URL constructed from the hostname (e.g., `wss://tun.example.com/ws`) used to establish the tunnel connection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can establish a tunnel by typing a command with fewer than 60 characters for the common case (port + server + key).
- **SC-002**: 95% of first-time users successfully connect on their first attempt when following the help text examples.
- **SC-003**: The time from reading the documentation to a working tunnel is under 2 minutes for a new user.
- **SC-004**: Connection error messages clearly identify the specific issue (missing arg, invalid port, connection refused, auth failed) with actionable guidance.
- **SC-005**: The client validates all inputs before attempting connection and provides specific, field-level error messages.

## Clarifications

### Session 2026-02-13

- Q: Should the client support a configuration file for default values like server URL and secret key? -> A: Support properties file for defaults
- Q: Should users be able to override the WebSocket protocol (ws:// vs wss://) for local development? -> A: Add `--insecure` flag for ws://
- Q: What subdomain format should the client validate? -> A: Enforce DNS label rules
- Q: What verbosity levels should the CLI support? -> A: Support `--quiet` and `--verbose` flags
- Q: Should the client use specific exit codes for different failure scenarios? -> A: Specific exit codes per failure type

## Assumptions and Dependencies

- The local HTTP service is assumed to be running on the specified port before the client starts.
- The server supports WebSocket connections at the `/ws` endpoint path.
- The server can assign random subdomains when none is requested.
- HTTPS/WSS is the default protocol; unencrypted connections are not supported in this simplified interface.
- The `localhost` interface is sufficient for all local service connections.
- Quarkus Picocli extension is available in the project dependencies and will handle all CLI parsing, replacing the existing manual argument parsing implementation.
- The client supports a properties file (e.g., `~/.relay/config.properties` or `application.properties`) for default configuration values, with CLI arguments and environment variables taking precedence.
