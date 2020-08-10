package com.ppaass.kt.agent.handler.http.uitl

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
import org.slf4j.LoggerFactory
import java.util.*

internal object HttpProxyUtil {
    private val logger = LoggerFactory.getLogger(
            HttpProxyUtil::class.java)

    private fun convertHttpRequest(httpRequest: HttpRequest): ByteArray {
        val ch = EmbeddedChannel(HttpRequestEncoder())
        ch.writeOutbound(httpRequest)
        val httpRequestByteBuf = ch.readOutbound<ByteBuf>()
        val data = ByteBufUtil.getBytes(httpRequestByteBuf)
        return data
    }

    fun writeToProxy(bodyType: AgentMessageBodyType, secureToken: String, proxyChannel: Channel,
                     host: String, port: Int,
                     input: Any?,
                     clientChannelId: String, messageBodyEncryptionType: MessageBodyEncryptionType): ChannelFuture {
        var data: ByteArray? = null
        if (input != null) {
            data = if (input is HttpRequest) {
                convertHttpRequest(input)
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
}