package com.ppaass.proxy.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
@ChannelHandler.Sharable
internal class TargetTcpChannelHeartbeatHandler :
    ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun userEventTriggered(targetChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug(
                "Return because of it is not a connection idle event.")
            return
        }
        val targetChannel = targetChannelContext.channel()
        val tcpConnectionInfo = targetChannel.attr(TCP_CONNECTION_INFO).get()
        if (tcpConnectionInfo == null) {
            logger.error(
                "Close target channel because no connection information attached, target channel = {}",
                targetChannel.id().asLongText())
            targetChannel.close()
            return
        }
        val proxyChannel = tcpConnectionInfo.proxyTcpChannel
        if (evt.state() == IdleState.READER_IDLE) {
            if (proxyChannel.isWritable()) {
                targetChannel.read()
                logger.debug(
                    "Trigger read on target channel, target channel = {}, proxy channel = {}",
                    targetChannel.id().asLongText(), proxyChannel.id().asLongText())
            }
            return
        }
        if (evt.state() == IdleState.WRITER_IDLE) {
            if (targetChannel.isWritable) {
                proxyChannel.read()
                logger.debug(
                    "Trigger read on proxy channel, target channel = {}, proxy channel = {}",
                    targetChannel.id().asLongText(), proxyChannel.id().asLongText())
            }
            return
        }
        if (proxyChannel.isWritable) {
            targetChannel.read()
            logger.debug(
                "Trigger read on target channel, target channel = {}, proxy channel = {}",
                targetChannel.id().asLongText(), proxyChannel.id().asLongText())
        }
        if (targetChannel.isWritable) {
            proxyChannel.read()
            logger.debug(
                "Trigger read on proxy channel, target channel = {}, proxy channel = {}",
                targetChannel.id().asLongText(), proxyChannel.id().asLongText())
        }
    }
}
