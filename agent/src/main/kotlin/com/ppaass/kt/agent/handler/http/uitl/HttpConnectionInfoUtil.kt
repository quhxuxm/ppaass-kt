package com.ppaass.kt.agent.handler.http.uitl

import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.common.exception.PpaassException
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriComponentsBuilder

internal object HttpConnectionInfoUtil {
    private val logger = LoggerFactory.getLogger(
            HttpConnectionInfoUtil::class.java)
    private const val HTTP_SCHEMA = "http://"
    private const val HTTPS_SCHEMA = "https://"
    private const val SCHEMA_AND_HOST_SEP = "://"
    private const val HOST_NAME_AND_PORT_SEP = ":"
    private const val SLASH = "/"
    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_HTTPS_PORT = 443

    fun parseHttpConnectionInfo(uri: String): HttpConnectionInfo {
        if (uri.startsWith(HTTP_SCHEMA)) {
            val uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
            val uriComponents = uriComponentsBuilder.build()
            var port: Int = uriComponents.getPort()
            if (port < 0) {
                port = DEFAULT_HTTP_PORT
            }
            return HttpConnectionInfo(uriComponents.host ?: "",
                    port)
        }
        if (uri.startsWith(HTTPS_SCHEMA)) {
            val uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
            val uriComponents = uriComponentsBuilder.build()
            var port: Int = uriComponents.getPort()
            if (port < 0) {
                port =
                        DEFAULT_HTTPS_PORT
            }
            return HttpConnectionInfo(uriComponents.host ?: "",
                    port)
        }
        //For CONNECT method, only HTTPS will do this method.
        val schemaAndHostNameSepIndex = uri.indexOf(
                SCHEMA_AND_HOST_SEP)
        var hostNameAndPort = uri
        if (schemaAndHostNameSepIndex >= 0) {
            hostNameAndPort = uri.substring(
                    schemaAndHostNameSepIndex + SCHEMA_AND_HOST_SEP.length)
        }
        if (hostNameAndPort.contains(
                        SLASH)) {
            logger.error("Can not parse host name from uri: {}", uri)
            throw PpaassException("Can not parse host name from uri: $uri")
        }
        val hostNameAndPortParts =
                hostNameAndPort.split(
                        HOST_NAME_AND_PORT_SEP).toTypedArray()
        val hostName = hostNameAndPortParts[0]
        var port = DEFAULT_HTTPS_PORT
        if (hostNameAndPortParts.size > 1) {
            port = hostNameAndPortParts[1].toInt()
        }
        return HttpConnectionInfo(hostName, port)
    }
}