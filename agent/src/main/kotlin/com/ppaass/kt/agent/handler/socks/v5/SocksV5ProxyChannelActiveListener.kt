package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.EventExecutorGroup
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.slf4j.LoggerFactory

internal class SocksV5ProxyChannelActiveListener(private val socks5CommandRequest: Socks5CommandRequest,
                                                 private val agentChannelContext: ChannelHandlerContext,
                                                 private val businessEventExecutorGroup: EventExecutorGroup,
                                                 private val agentConfiguration: AgentConfiguration) :
        GenericFutureListener<Future<Channel>> {
    companion object {
        private val logger = LoggerFactory.getLogger(
                SocksV5ProxyChannelActiveListener::class.java)
    }

    override fun operationComplete(future: Future<Channel>) {
        if (!future.isSuccess) {
            return
        }
        val proxyChannel = future.now as Channel
        logger.debug(
                "Success connect to target server: {}:{}", this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort())
        agentChannelContext.pipeline()
                .addLast(businessEventExecutorGroup,
                        SocksV5AgentToProxyHandler(proxyChannel,
                                socks5CommandRequest, this.agentConfiguration))
        agentChannelContext.pipeline().addLast(ResourceClearHandler(proxyChannel))
        agentChannelContext.pipeline().remove(SocksV5ConnectCommandHandler::class.java.name)
        agentChannelContext.channel().writeAndFlush(DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                this.socks5CommandRequest.dstAddrType(),
                this.socks5CommandRequest.dstAddr(),
                this.socks5CommandRequest.dstPort()))
    }
}