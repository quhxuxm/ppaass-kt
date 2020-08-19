package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.*
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.concurrent.EventExecutorGroup
import mu.KotlinLogging
import java.util.*

internal class TargetToProxyHandler(private val proxyChannelContext: ChannelHandlerContext,
                                    private val agentMessage: AgentMessage, private val dataTransferExecutorGroup: EventExecutorGroup,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }


    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        proxyChannelContext.pipeline().addLast(dataTransferExecutorGroup,
                ProxyToTargetHandler(
                        targetChannel = targetChannel,
                        proxyConfiguration = proxyConfiguration))
        proxyChannelContext.fireChannelRead(agentMessage)
        if (!proxyConfiguration.autoRead) {
            targetChannel.read()
        }
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, UUID.randomUUID().toString().replace("-", ""))
        proxyMessageBody.targetAddress = agentMessage.body.targetAddress
        proxyMessageBody.targetPort = agentMessage.body.targetPort
        proxyMessageBody.originalData = originalDataByteArray
        val proxyMessage =
                ProxyMessage(UUID.randomUUID().toString(), MessageBodyEncryptionType.random(), proxyMessageBody)
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        val proxyChannel = proxyChannelContext.channel()
        if (!proxyChannel.isActive) {
            logger.error("Fail to transfer data from target to proxy server because of proxy channel is not active.")
            throw PpaassException(
                    "Fail to transfer data from target to proxy server because of proxy channel is not active.")
        }
        proxyChannel.eventLoop().execute {
            logger.debug { "Write proxy message to agent, proxyMessage=\n$proxyMessage\n" }
            proxyChannel.writeAndFlush(proxyMessage).addListener {
                if (!it.isSuccess) {
                    proxyChannel.close()
                    targetChannelContext.close()
                    logger.error("Fail to write proxy message to agent, target=${agentMessage.body.targetAddress}:${agentMessage.body.targetPort}", it.cause())
                    throw PpaassException("Fail to write proxy message to agent, target=${agentMessage.body.targetAddress}:${agentMessage.body.targetPort}")
                }
                if (!proxyConfiguration.autoRead) {
                    targetChannelContext.channel().read()
                }
            }
        }
    }

    override fun channelReadComplete(targetChannelContext: ChannelHandlerContext) {
        proxyChannelContext.flush()
    }
}
