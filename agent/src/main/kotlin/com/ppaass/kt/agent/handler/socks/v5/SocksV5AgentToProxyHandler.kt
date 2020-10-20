package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBody
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5AgentToProxyHandler(
    private val agentConfiguration: AgentConfiguration) :
    SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: ByteBuf) {
        val agentChannel = agentChannelContext.channel()
        val socks5CommandRequest = agentChannel.attr(SOCKS_V5_COMMAND_REQUEST).get()
        val proxyChannelContext = agentChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        val data = ByteArray(msg.readableBytes())
        msg.readBytes(data)
        val agentMessageBody =
            AgentMessageBody(AgentMessageBodyType.DATA, agentChannelContext.channel().id().asLongText(),
                this.agentConfiguration.userToken)
        agentMessageBody.targetAddress = socks5CommandRequest.dstAddr()
        agentMessageBody.targetPort = socks5CommandRequest.dstPort()
        agentMessageBody.originalData = data
        val agentMessage = AgentMessage(
            encryptionToken = generateUid(),
            messageBodyEncryptionType = MessageBodyEncryptionType.random(),
            body = agentMessageBody)
        if (!proxyChannel.isActive) {
            proxyChannel.close()
            agentChannelContext.close()
            logger.debug(
                "Fail to send connect message from agent to proxy because of proxy channel not active.")
            return
        }
        proxyChannel.writeAndFlush(agentMessage)
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        val agentChannel = agentChannelContext.channel()
        val proxyChannelContext = agentChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        proxyChannel.flush()
        agentChannelContext.flush()
    }
}
