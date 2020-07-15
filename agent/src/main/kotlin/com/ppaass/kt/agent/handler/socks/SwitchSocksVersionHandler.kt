package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.socks.v4.SocksV4Handler
import com.ppaass.kt.agent.handler.socks.v5.SocksV5Handler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class SwitchSocksVersionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<SocksMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(SwitchSocksVersionHandler::class.java)
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socksRequest: SocksMessage) {
        val clientChannelId = agentChannelContext.channel().id().asLongText();
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            logger.error(
                    "Incoming protocol is unknown protocol, clientChannelId={}.", clientChannelId)
            agentChannelContext.close()
            return
        }
        val agentChannelPipeline = agentChannelContext.pipeline();
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            logger.debug("Incoming request socks4/4a, clientChannelId={}", clientChannelId)
            with(agentChannelPipeline) {
                addLast(SocksV4Handler::class.java.name,
                        SocksV4Handler(agentConfiguration))
            }
            agentChannelContext.fireChannelRead(socksRequest)
            return
        }
        with(agentChannelPipeline) {
            logger.debug("Incoming request socks5, clientChannelId={}", clientChannelId)
            addLast(SocksV5Handler::class.java.name,
                    SocksV5Handler(agentConfiguration))
        }
        agentChannelContext.fireChannelRead(socksRequest)
        return
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}