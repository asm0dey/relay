package org.relay.shared.protocol

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payload for RESPONSE messages containing HTTP response data.
 */
data class ResponsePayload(
    @param:JsonProperty("statusCode")
    val statusCode: Int,

    @param:JsonProperty("headers")
    val headers: Map<String, String>,

    @param:JsonProperty("body")
    val body: String? = null
) {
    companion object {
        const val STATUS_CODE_FIELD = "statusCode"
        const val HEADERS_FIELD = "headers"
        const val BODY_FIELD = "body"
    }

    override fun toString(): String {
        return "ResponsePayload(statusCode=$statusCode, headers=${headers.keys}, bodyPresent=${body != null})"
    }
}
