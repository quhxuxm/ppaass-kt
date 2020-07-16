package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.*
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory

internal class SocksV5ProxyToAgentHandler(private val agentChannel: Channel,
                                          private val socks5CommandRequest: Socks5CommandRequest,
                                          private val agentConfiguration: AgentConfiguration,
                                          private val proxyChannelActivePromise: Promise<Channel>) :
        SimpleChannelInboundHandler<ProxyMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(SocksV5ProxyToAgentHandler::class.java)
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val agentChannelId = agentChannel.id().asLongText()
        val agentMessageBody = AgentMessageBody(bodyType = AgentMessageBodyType.CONNECT, id = agentChannelId,
                targetAddress = socks5CommandRequest.dstAddr(), targetPort = socks5CommandRequest.dstPort())
        val agentMessage = AgentMessage(
                secureToken = agentConfiguration.userToken,
                messageBodyEncryptionType = MessageBodyEncryptionType.random(),
                body = agentMessageBody)
        proxyChannelContext.channel().writeAndFlush(agentMessage).addListener(
                GenericFutureListener { future: Future<in Void?> ->
                    if (!future.isSuccess) {
                        this.proxyChannelActivePromise.setFailure(future.cause())
                        logger.error(
                                "Fail to send connect message from agent to proxy because of exception.",
                                future.cause())
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy because of exception.",
                                future.cause())
                    }
                    this.proxyChannelActivePromise.setSuccess(proxyChannelContext.channel())
                })
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, msg: ProxyMessage) {
        val originalDataBuf = Unpooled.wrappedBuffer(msg.body.originalData)
        agentChannel.writeAndFlush(originalDataBuf)
    }

    override fun channelReadComplete(proxyChannelContext: ChannelHandlerContext) {
        agentChannel.flush()
    }
}