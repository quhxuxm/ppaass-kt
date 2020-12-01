package com.ppaass.agent.handler.socks

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessage
import com.ppaass.kt.common.AgentMessageBody
import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.generateUuid
import com.ppaass.kt.common.generateUuidInBytes
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.channels.ClosedChannelException

private class SocksAgentToProxyTcpChannelWriteDataListener(
    private val agentChannel: Channel,
    private val agentMessage: AgentMessage,
    private val agentConfiguration: AgentConfiguration) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes: Int = 0;

    override fun operationComplete(proxyChannelFuture: ChannelFuture) {
        if (proxyChannelFuture.isSuccess) {
            return
        }
        val proxyChannel = proxyChannelFuture.channel()
        val cause = proxyChannelFuture.cause()
        if (cause is ClosedChannelException) {
            logger.error {
                "Fail write agent original message to proxy because of proxy channel closed already, agent channel = ${
                    agentChannel.id().asLongText()
                }, proxy channel = ${
                    proxyChannel.id().asLongText()
                }, target address = ${
                    agentMessage.body.targetHost
                }, target port = ${
                    agentMessage.body.targetPort
                }, target connection type = ${
                    agentMessage.body.bodyType
                }"
            }
            agentChannel.close()
            return
        }
        if (failureTimes >=
            agentConfiguration.agentToProxyTcpChannelWriteRetry) {
            logger.error(cause) {
                "Fail write agent original message to proxy because of exception, agent channel = ${
                    agentChannel.id().asLongText()
                }, proxy channel = ${
                    proxyChannel.id().asLongText()
                }, target address = ${
                    agentMessage.body.targetHost
                }, target port = ${
                    agentMessage.body.targetPort
                }, target connection type = ${
                    agentMessage.body.bodyType
                }"
            }
            agentChannel.close()
            return
        }
        failureTimes++
        logger.error(cause) {
            "Retry write to proxy (${failureTimes}), proxy channel = ${
                proxyChannel.id().asLongText()
            }, agent message: \n${agentMessage}\n"
        }
        proxyChannelFuture.channel().writeAndFlush(agentMessage).addListener(this)
    }
}

@ChannelHandler.Sharable
@Service
internal class SocksAgentToProxyTcpChannelHandler(
    private val agentConfiguration: AgentConfiguration
) : SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext,
                              originalAgentData: ByteBuf) {
        val agentChannel = agentChannelContext.channel()
        val tcpConnectionInfo = agentChannel.attr(SOCKS_TCP_CONNECTION_INFO).get()
        if (tcpConnectionInfo == null) {
            logger.error(
                "Fail write agent original message to proxy because of no connection information attached, agent channel = {}",
                agentChannel.id().asLongText())
            return
        }
        val proxyTcpChannel = tcpConnectionInfo.proxyTcpChannel
        val originalAgentDataByteArray = ByteArray(originalAgentData.readableBytes())
        originalAgentData.readBytes(originalAgentDataByteArray)
        val agentMessageBody = AgentMessageBody(
            id = generateUuid(),
            bodyType = AgentMessageBodyType.TCP_DATA,
            userToken = agentConfiguration.userToken!!,
            targetHost = tcpConnectionInfo.targetHost,
            targetPort = tcpConnectionInfo.targetPort,
            data = originalAgentDataByteArray)
        val agentMessage = AgentMessage(
            encryptionToken = generateUuidInBytes(),
            encryptionType = EncryptionType.choose(),
            body = agentMessageBody)
        logger.debug { "Write agent message to proxy: \n${agentMessage}\n" }
        proxyTcpChannel.writeAndFlush(agentMessage)
            .addListener(SocksAgentToProxyTcpChannelWriteDataListener(agentChannel, agentMessage,
                agentConfiguration))
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        val agentChannel = agentChannelContext.channel()
        val tcpConnectionInfo = agentChannel.attr(SOCKS_TCP_CONNECTION_INFO).get()
        if (tcpConnectionInfo == null) {
            agentChannelContext.flush()
            agentChannelContext.fireChannelReadComplete()
            return
        }
        val proxyTcpChannel = tcpConnectionInfo.proxyTcpChannel
        proxyTcpChannel.flush()
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable) {
        val agentChannel = agentChannelContext.channel()
        val tcpConnectionInfo = agentChannel.attr(SOCKS_TCP_CONNECTION_INFO).get()
        logger.error(cause) {
            "Exception happen on agent channel, agent channel = ${
                agentChannel.id().asLongText()
            }, proxy channel = ${
                tcpConnectionInfo?.proxyTcpChannel?.id()?.asLongText()
            }"
        }
    }
}
