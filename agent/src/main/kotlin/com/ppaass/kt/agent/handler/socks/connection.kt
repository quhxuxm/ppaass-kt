package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.AgentConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
class SwitchSocksVersionConnectionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<SocksMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(SwitchSocksVersionConnectionHandler::class.java)
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socksRequest: SocksMessage) {
        val clientChannelId = agentChannelContext.channel().id().asLongText();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            logger.error(
                    "Incoming protocol is unknown protocol, clientChannelId={}.", clientChannelId)
            agentChannelContext.close()
            return
        }
        val channelPipeline = agentChannelContext.pipeline();
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            logger.debug("Incoming request socks4/4a, clientChannelId={}", clientChannelId)
            with(channelPipeline) {
                addLast(SocksV4ConnectionHandler::class.java.name, SocksV4ConnectionHandler(agentConfiguration))
            }
            agentChannelContext.fireChannelRead(socksRequest)
            return
        }
        with(channelPipeline) {
            logger.debug("Incoming request socks5, clientChannelId={}", clientChannelId)
            addLast(SocksV5ConnectionHandler::class.java.name, SocksV5ConnectionHandler(agentConfiguration))
        }
        agentChannelContext.fireChannelRead(socksRequest)
        return
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}