package org.relay.client.command

/**
 * Log level enum for output verbosity control.
 * Maps CLI flags to appropriate logging levels.
 */
enum class LogLevel {
    ERROR,
    INFO,
    DEBUG;

    companion object {
        /**
         * Determines the log level from CLI flags.
         *
         * When both quiet and verbose are set, ignores both and returns INFO (default).
         *
         * @param quiet true if --quiet flag is set (errors only)
         * @param verbose true if --verbose flag is set (debug logging)
         * @return the appropriate LogLevel
         */
        fun fromFlags(quiet: Boolean, verbose: Boolean): LogLevel {
            return when {
                quiet && verbose -> INFO  // Conflicting flags: ignore both
                quiet -> ERROR
                verbose -> DEBUG
                else -> INFO
            }
        }
    }
}
