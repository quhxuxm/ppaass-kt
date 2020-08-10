package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBody
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestEncoder
import mu.KotlinLogging
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

private val logger = KotlinLogging.logger {}
private const val HTTP_SCHEMA = "http://"
private const val HTTPS_SCHEMA = "https://"
private const val SCHEMA_AND_HOST_SEP = "://"
private const val HOST_NAME_AND_PORT_SEP = ":"
private const val SLASH = "/"
private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

internal object ChannelInfoCache {
    private val cache = Hashtable<String, ChannelInfo>()
    private val logger = KotlinLogging.logger {}

    fun getChannelInfo(clientId: String): ChannelInfo? {
        logger.debug { "Get channel info by id=$clientId" }
        return cache[clientId]
    }

    fun saveChannelInfo(clientId: String, channelInfo: ChannelInfo) {
        cache[clientId] = channelInfo
        logger.debug { "Save channel info by id=$clientId" }
    }

    fun removeChannelInfo(clientId: String) {
        cache.remove(clientId)
        logger.debug { "Remove channel info by id=$clientId" }
    }
}

internal fun parseHttpConnectionInfo(uri: String): HttpConnectionInfo {
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


fun writeAgentMessageToProxy(bodyType: AgentMessageBodyType, secureToken: String, proxyChannel: Channel,
                             host: String, port: Int,
                             input: Any?,
                             clientChannelId: String, messageBodyEncryptionType: MessageBodyEncryptionType): ChannelFuture {
    var data: ByteArray? = null
    if (input != null) {
        data = if (input is HttpRequest) {
            val ch = EmbeddedChannel(HttpRequestEncoder())
            ch.writeOutbound(input)
            val httpRequestByteBuf = ch.readOutbound<ByteBuf>()
            ByteBufUtil.getBytes(httpRequestByteBuf)
        } else {
            ByteBufUtil.getBytes(input as ByteBuf)
        }
    }
    val agentMessageBody = AgentMessageBody(bodyType, clientChannelId, secureToken)
    agentMessageBody.originalData = data
    agentMessageBody.targetAddress = host
    agentMessageBody.targetPort = port
    val agentMessage =
            AgentMessage(UUID.randomUUID().toString(), messageBodyEncryptionType, agentMessageBody)
    if (data != null) {
        logger.debug("The agent message write from agent to proxy is:\n{}\n", agentMessage)
    }
    return proxyChannel.writeAndFlush(agentMessage)
}
