package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBody
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.Future
import mu.KotlinLogging

internal class SocksV5ProxyToAgentHandler(private val agentChannel: Channel,
                                          private val socks5CommandRequest: Socks5CommandRequest,
                                          private val agentConfiguration: AgentConfiguration) :
    SimpleChannelInboundHandler<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val agentChannelId = agentChannel.id().asLongText()
        val agentMessageBody = AgentMessageBody(bodyType = AgentMessageBodyType.CONNECT, id = agentChannelId,
            securityToken = agentConfiguration.userToken,
            targetAddress = socks5CommandRequest.dstAddr(), targetPort = socks5CommandRequest.dstPort())
        val agentMessage = AgentMessage(
            encryptionToken = generateUid(),
            messageBodyEncryptionType = MessageBodyEncryptionType.random(),
            body = agentMessageBody)
        agentChannel.pipeline().apply {
            if (this[SocksV5ConnectCommandHandler::class.java] != null) {
                remove(SocksV5ConnectCommandHandler::class.java)
            }
            if (this[SocksV5AgentToProxyHandler::class.java] == null) {
                addLast(proxyChannelContext.executor(),
                    SocksV5AgentToProxyHandler(proxyChannelContext.channel(),
                        socks5CommandRequest, agentConfiguration))
            }
        }
        proxyChannelContext.channel().writeAndFlush(agentMessage).addListener { future: Future<in Void?> ->
            if (!future.isSuccess) {
                proxyChannelContext.close()
                agentChannel.writeAndFlush(
                    DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        socks5CommandRequest.dstAddrType()))
                    .addListener(ChannelFutureListener.CLOSE)
                logger.debug(
                    "Fail to send connect message from agent to proxy because of exception.",
                    future.cause())
                return@addListener
            }
            logger.debug(
                "Success connect to target server: {}:{}", socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort())
            agentChannel.writeAndFlush(DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                socks5CommandRequest.dstAddrType(),
                socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort()))
        }
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, msg: ProxyMessage) {
        val originalDataBuf = Unpooled.wrappedBuffer(msg.body.originalData)
        if (!agentChannel.isActive) {
            proxyChannelContext.close()
            agentChannel.close()
            logger.debug(
                "Fail to send message from proxy to agent because of agent channel not active.")
            return
        }
        agentChannel.writeAndFlush(originalDataBuf)
    }

    override fun channelReadComplete(proxyChannelContext: ChannelHandlerContext) {
        agentChannel.flush()
    }
}
