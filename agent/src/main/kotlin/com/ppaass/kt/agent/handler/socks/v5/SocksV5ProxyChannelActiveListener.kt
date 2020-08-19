package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import mu.KotlinLogging

internal class SocksV5ProxyChannelActiveListener(private val socks5CommandRequest: Socks5CommandRequest,
                                                 private val agentChannelContext: ChannelHandlerContext,
                                                 private val agentConfiguration: AgentConfiguration) :
        GenericFutureListener<Future<Channel>> {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val resourceClearHandler = ResourceClearHandler()
    }

    override fun operationComplete(future: Future<Channel>) {
        if (!future.isSuccess) {
            return
        }
        val proxyChannel = future.now as Channel
        logger.debug(
                "Success connect to target server: {}:{}", this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort())
        agentChannelContext.pipeline().apply {
            addLast(
                    SocksV5AgentToProxyHandler(proxyChannel,
                            socks5CommandRequest, agentConfiguration))
            addLast(resourceClearHandler)
            if (this[SocksV5ConnectCommandHandler::class.java.name] != null) {
                remove(SocksV5ConnectCommandHandler::class.java.name)
            }
        }
        agentChannelContext.channel().writeAndFlush(DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                this.socks5CommandRequest.dstAddrType(),
                this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort()))
    }
}
