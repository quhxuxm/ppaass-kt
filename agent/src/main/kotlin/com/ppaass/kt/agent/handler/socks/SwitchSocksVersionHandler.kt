package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.socks.v5.SocksV5Handler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SwitchSocksVersionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<SocksMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val socksV5Handler = SocksV5Handler(this.agentConfiguration)

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
            logger.error(
                    "Socks4a not support, clientChannelId={}.", clientChannelId)
            agentChannelContext.close()
            return
        }
        agentChannelPipeline.apply {
            logger.debug("Incoming request socks5, clientChannelId={}", clientChannelId)
            addLast(SocksV5Handler::class.java.name, socksV5Handler)
            remove(this@SwitchSocksVersionHandler)
        }
        agentChannelContext.fireChannelRead(socksRequest)
        return
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}
