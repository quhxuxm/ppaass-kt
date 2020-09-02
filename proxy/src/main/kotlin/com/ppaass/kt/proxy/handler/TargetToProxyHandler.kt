package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.*
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging

internal class TargetToProxyHandler(private val proxyChannelContext: ChannelHandlerContext,
                                    private val agentMessage: AgentMessage,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        proxyChannelContext.pipeline().apply {
            if (this[SetupTargetConnectionHandler::class.java] != null) {
                remove(SetupTargetConnectionHandler::class.java)
            }
            addLast(targetChannelContext.executor(),
                    ProxyToTargetHandler(
                            targetChannel = targetChannel,
                            proxyConfiguration = proxyConfiguration))
        }
        proxyChannelContext.fireChannelRead(agentMessage)
        targetChannel.read()
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, generateUid())
        proxyMessageBody.targetAddress = agentMessage.body.targetAddress
        proxyMessageBody.targetPort = agentMessage.body.targetPort
        proxyMessageBody.originalData = originalDataByteArray
        val proxyMessage =
                ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        val proxyChannel = proxyChannelContext.channel()
        logger.debug { "Write proxy message to agent, proxyMessage=\n$proxyMessage\n" }
        proxyChannel.writeAndFlush(proxyMessage).addListener {
            targetChannelContext.channel().read()
        }
    }
}
