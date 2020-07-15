package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.message.AgentMessage
import com.ppaass.kt.common.message.AgentMessageBody
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

internal class SocksV5AgentToProxyHandler(private val proxyChannel: Channel,
                                          private val socks5CommandRequest: Socks5CommandRequest,
                                          private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: ByteBuf) {
        val data = ByteBufUtil.getBytes(msg)
        val agentMessageBody =
                AgentMessageBody(AgentMessageBodyType.DATA, agentChannelContext.channel().id().asLongText())
        agentMessageBody.targetAddress = socks5CommandRequest.dstAddr()
        agentMessageBody.targetPort = socks5CommandRequest.dstPort()
        agentMessageBody.originalData = data
        val agentMessage = AgentMessage(
                secureToken = this.agentConfiguration.userToken,
                messageBodyEncryptionType = MessageBodyEncryptionType.random(),
                body = agentMessageBody)
        proxyChannel.writeAndFlush(agentMessage)
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        this.proxyChannel.flush()
    }
}