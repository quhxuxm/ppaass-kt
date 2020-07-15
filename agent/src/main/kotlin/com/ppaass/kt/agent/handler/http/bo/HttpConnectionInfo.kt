package com.ppaass.kt.agent.handler.http.bo

internal data class HttpConnectionInfo(val host: String, val port: Int) {
    override fun toString(): String {
        return "HttpConnectionInfo(host='$host', port=$port)"
    }
}