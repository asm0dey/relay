package org.relay.shared.protocol

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payload for REQUEST messages containing HTTP request data.
 */
data class RequestPayload(
    @param:JsonProperty("method")
    val method: String,

    @param:JsonProperty("path")
    val path: String,

    @param:JsonProperty("query")
    val query: Map<String, String>? = null,

    @param:JsonProperty("headers")
    val headers: Map<String, String>,

    @param:JsonProperty("body")
    val body: String? = null,

    @param:JsonProperty("webSocketUpgrade")
    val webSocketUpgrade: Boolean = false
) {
    companion object {
        const val METHOD_FIELD = "method"
        const val PATH_FIELD = "path"
        const val QUERY_FIELD = "query"
        const val HEADERS_FIELD = "headers"
        const val BODY_FIELD = "body"
    }

    override fun toString(): String {
        return "RequestPayload(method='$method', path='$path', query=$query, headers=${headers.keys}, bodyPresent=${body != null})"
    }
}
