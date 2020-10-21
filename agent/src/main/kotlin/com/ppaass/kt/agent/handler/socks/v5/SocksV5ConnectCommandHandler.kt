package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ConnectCommandHandler(
    private val agentConfiguration: AgentConfiguration,
    private val socksV5ProxyServerBootstrap: Bootstrap) :
    SimpleChannelInboundHandler<Socks5CommandRequest>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socks5CommandRequest: Socks5CommandRequest) {
        logger.debug {
            "Create new connection to proxy server, agentChannelId=${
                agentChannelContext.channel().id().asLongText()
            }"
        }
        this.socksV5ProxyServerBootstrap.connect(agentConfiguration.proxyAddress, agentConfiguration.proxyPort)
            .addListener((ChannelFutureListener {
                if (!it.isSuccess) {
                    agentChannelContext.channel().writeAndFlush(
                        DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                            socks5CommandRequest.dstAddrType()))
                        .addListener(ChannelFutureListener.CLOSE)
                    return@ChannelFutureListener
                }
                it.channel().attr(AGENT_CHANNEL_CONTEXT).set(agentChannelContext)
                it.channel().attr(SOCKS_V5_COMMAND_REQUEST).set(socks5CommandRequest)
            }))
    }
}
