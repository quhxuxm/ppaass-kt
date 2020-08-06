package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.AgentMessage
import com.ppaass.kt.common.message.AgentMessageBody
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import org.slf4j.LoggerFactory
import java.util.*

internal class SocksV5AgentToProxyHandler(private val proxyChannel: Channel,
                                          private val socks5CommandRequest: Socks5CommandRequest,
                                          private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = LoggerFactory.getLogger(SocksV5AgentToProxyHandler::class.java)
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: ByteBuf) {
        val data = ByteArray(msg.readableBytes())
        msg.readBytes(data)
        val agentMessageBody =
                AgentMessageBody(AgentMessageBodyType.DATA, agentChannelContext.channel().id().asLongText(),
                        this.agentConfiguration.userToken)
        agentMessageBody.targetAddress = socks5CommandRequest.dstAddr()
        agentMessageBody.targetPort = socks5CommandRequest.dstPort()
        agentMessageBody.originalData = data
        val agentMessage = AgentMessage(
                encryptionToken = UUID.randomUUID().toString(),
                messageBodyEncryptionType = MessageBodyEncryptionType.random(),
                body = agentMessageBody)
        if (!proxyChannel.isActive) {
            logger.error(
                    "Fail to send connect message from agent to proxy because of proxy channel not active.")
            throw PpaassException(
                    "Fail to send connect message from agent to proxy because of proxy channel not active.")
        }
        proxyChannel.writeAndFlush(agentMessage)
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        this.proxyChannel.flush()
    }
}