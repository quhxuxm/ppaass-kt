package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SetupTargetConnectionHandler(private val targetBootstrap: Bootstrap) :
    SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              agentMessage: AgentMessage) {
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.error(
                "Return because of targetAddress is null, message id=${agentMessage.body.id}")
            throw PpaassException(
                "Return because of targetAddress is null, message id=${agentMessage.body.id}")
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            throw PpaassException(
                "Return because of targetPort is null, message id=${agentMessage.body.id}")
        }
        logger.debug(
            "Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        val targetConnectFuture = this.targetBootstrap.connect(targetAddress, targetPort)
        targetConnectFuture
            .addListener(
                TargetConnectListener(targetConnectFuture, proxyChannelContext, targetAddress,
                    targetPort, agentMessage, listOf(this))
            )
    }
}
