package com.ppaass.agent.handler.http

import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.channels.ClosedChannelException

@Sharable
@Service
internal class HttpProxyToAgentHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        val proxyChannel = proxyChannelContext.channel()
        val connectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        if (connectionInfo == null) {
            logger.error(
                "Fail to transfer data from proxy to agent because of no connection information attached, proxy channel = {}.",
                proxyChannel.id().asLongText())
            proxyChannel.close()
            return
        }
        val agentChannel = connectionInfo.agentChannel!!
        agentChannel.writeAndFlush(msg).addListener(
            ChannelFutureListener { agentChannelFuture: ChannelFuture ->
                if (agentChannelFuture.isSuccess) {
                    return@ChannelFutureListener
                }
                if (agentChannelFuture.cause() is ClosedChannelException) {
                    logger.error {
                        "Fail to transfer data from agent to client because of agent channel closed already, agent channel = ${
                            agentChannel.id().asLongText()
                        }, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }, target address = ${
                            connectionInfo.targetHost
                        }, target port = ${
                            connectionInfo.targetPort
                        }"
                    }
                    proxyChannel.close()
                    return@ChannelFutureListener
                }
                logger.error(agentChannelFuture.cause()) {
                    "Fail to transfer data from agent to client because of exception, agent channel = ${
                        agentChannel.id().asLongText()
                    }, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target address = ${
                        connectionInfo.targetHost
                    }, target port = ${
                        connectionInfo.targetPort
                    }."
                }
                proxyChannel.close()
            })
    }

    @Throws(Exception::class)
    override fun exceptionCaught(proxyChannelContext: ChannelHandlerContext, cause: Throwable) {
        val proxyChannel = proxyChannelContext.channel()
        val connectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        val agentChannel = connectionInfo?.agentChannel
        logger.error("Exception happen on proxy channel, agent channel = {}, proxy channel = {}",
            agentChannel?.id()?.asLongText() ?: "", proxyChannel.id().asLongText(), cause)
    }
}
