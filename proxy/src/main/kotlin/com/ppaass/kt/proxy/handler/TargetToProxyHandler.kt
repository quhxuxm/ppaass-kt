package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetToProxyHandler(
    private val dataTransferIoEventLoopGroup: EventLoopGroup,
    private val proxyToTargetHandler: ProxyToTargetHandler,
    private val setupTargetConnectionHandler: SetupTargetConnectionHandler
) : SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        val proxyChannel = proxyChannelContext.channel()
        proxyChannel.attr(TARGET_CHANNEL_CONTEXT).setIfAbsent(targetChannelContext)
        when (agentConnectMessage.body.bodyType) {
            AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE -> {
                if (targetChannel.isOpen) {
                    targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
                }
                if (proxyChannel.isOpen) {
                    proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
                }
            }
            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE -> {
                if (targetChannel.isOpen) {
                    targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                }
                if (proxyChannel.isOpen) {
                    proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                }
            }
            else -> {
                logger.error { "Incoming agent connect message do not have a suitable type: $agentConnectMessage" }
                throw PpaassException("Incoming agent connect message do not have a suitable type.")
            }
        }
        proxyChannelContext.pipeline().apply {
            remove(setupTargetConnectionHandler)
            addLast(dataTransferIoEventLoopGroup, proxyToTargetHandler)
        }
        if (proxyChannel.isWritable) {
            targetChannel.read()
        }
        proxyChannelContext.fireChannelRead(agentConnectMessage)
    }

    override fun channelInactive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        targetChannel.attr(PROXY_CHANNEL_CONTEXT).set(null)
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        targetChannel.attr(AGENT_CONNECT_MESSAGE).set(null)
        if (agentConnectMessage != null) {
            val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.TARGET_CHANNEL_CLOSE, generateUid())
            proxyMessageBody.targetAddress = agentConnectMessage.body.targetAddress
            proxyMessageBody.targetPort = agentConnectMessage.body.targetPort
            val proxyMessage =
                ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
            proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener.CLOSE)
        } else {
            if (proxyChannelContext != null) {
                proxyChannelContext.close()
            }
        }
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, generateUid())
        proxyMessageBody.targetAddress = agentConnectMessage.body.targetAddress
        proxyMessageBody.targetPort = agentConnectMessage.body.targetPort
        proxyMessageBody.originalData = originalDataByteArray
        val proxyMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        if (logger.isDebugEnabled) {
            logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        }
        proxyChannel.write(proxyMessage).addListener {
            if (proxyChannel.isWritable) {
                targetChannel.read()
            } else {
                proxyChannel.flush()
            }
        }
        proxyChannel.flush()
    }

    override fun channelWritabilityChanged(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        if (targetChannel.isWritable) {
            if (logger.isDebugEnabled) {
                logger.debug {
                    "Recover auto read on proxy channel: ${
                        proxyChannelContext.channel().id().asLongText()
                    }"
                }
            }
            proxyChannel.read()
        } else {
            targetChannel.flush()
            proxyChannel.flush()
        }
    }
}
