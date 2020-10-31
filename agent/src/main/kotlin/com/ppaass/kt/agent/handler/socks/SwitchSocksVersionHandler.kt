package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.handler.socks.v5.PROXY_CHANNEL_CONTEXT
import com.ppaass.kt.agent.handler.socks.v5.SocksV5ProtocolHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SwitchSocksVersionHandler(
    private val socksV5ProtocolHandler: SocksV5ProtocolHandler) :
    SimpleChannelInboundHandler<SocksMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext,
                              socksRequest: SocksMessage) {
        val clientChannelId = agentChannelContext.channel().id().asLongText();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            logger.error(
                "Incoming protocol is unknown protocol, clientChannelId={}.", clientChannelId)
            agentChannelContext.close()
            return
        }
        val agentChannelPipeline = agentChannelContext.pipeline();
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            logger.error(
                "Socks4a not support, clientChannelId={}.", clientChannelId)
            agentChannelContext.close()
            return
        }
        agentChannelPipeline.apply {
            logger.debug("Incoming request socks5, clientChannelId={}", clientChannelId)
            addLast(SocksV5ProtocolHandler::class.java.name,
                socksV5ProtocolHandler)
            remove(this@SwitchSocksVersionHandler)
        }
        agentChannelContext.fireChannelRead(socksRequest)
        return
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable) {
        val agentChannel = agentChannelContext.channel()
        val proxyChannelContext = agentChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext?.channel()
        logger.error(cause) {
            "Exception happen on agent channel, agent channel = ${
                agentChannel.id().asLongText()
            }, proxy channel = ${
                proxyChannel?.id()?.asLongText()
            }."
        }
    }
}
