package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.SocketException

@ChannelHandler.Sharable
@Service
internal class TargetToProxyHandler(
    private val dataTransferIoEventLoopGroup: EventLoopGroup,
    private val proxyToTargetHandler: ProxyToTargetHandler,
    private val targetBootstrap: Bootstrap
) : SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        if (proxyChannelContext == null) {
            return
        }
        if (agentConnectMessage == null) {
            return
        }
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
                this.setupProxyChannelPipelineForConnectMessage(proxyChannelContext, targetChannel)
            }
            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE -> {
                if (targetChannel.isOpen) {
                    targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                }
                if (proxyChannel.isOpen) {
                    proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                }
                this.setupProxyChannelPipelineForConnectMessage(proxyChannelContext, targetChannel)
            }
            else -> {
                logger.debug { "Nothing to do , because of incoming agent connect message is not a CONNECT message: $agentConnectMessage" }
            }
        }
        proxyChannelContext.fireChannelRead(agentConnectMessage)
        if (proxyChannel.isWritable) {
            targetChannel.read()
        } else {
            proxyChannel.flush()
        }
    }

    private fun setupProxyChannelPipelineForConnectMessage(
        proxyChannelContext: ChannelHandlerContext,
        targetChannel: Channel) {
        proxyChannelContext.pipeline().apply {
            val handlersToRemove = targetChannel.attr(HANDLERS_TO_REMOVE_AFTER_TARGET_ACTIVE).get()
            handlersToRemove?.forEach {
                try {
                    remove(it)
                } catch (e: NoSuchElementException) {
                    logger.debug { "The handler removed from pipeline already, handler = $it" }
                }
            }
            addLast(dataTransferIoEventLoopGroup, proxyToTargetHandler)
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
            logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n",
                proxyMessage)
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
        }
    }

    override fun exceptionCaught(targetChannelContext: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) {
            "Exception happen on target channel ${
                targetChannelContext.channel().id().asLongText()
            }."
        }
        if (cause is SocketException) {
            val targetChannel = targetChannelContext.channel()
            val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
            if (proxyChannelContext == null) {
                targetChannelContext.fireExceptionCaught(cause)
                return
            }
            val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
            if (agentConnectMessage == null) {
                targetChannelContext.fireExceptionCaught(cause)
                return
            }
            val targetAddress = agentConnectMessage.body.targetAddress
            if (targetAddress == null) {
                targetChannelContext.fireExceptionCaught(cause)
                return
            }
            val targetPort = agentConnectMessage.body.targetPort
            if (targetPort == null) {
                targetChannelContext.fireExceptionCaught(cause)
                return
            }
            targetChannelContext.close()
            val targetConnectFuture = this.targetBootstrap.connect()
            targetConnectFuture.addListener(TargetConnectListener(
                targetConnectFuture,
                proxyChannelContext,
                targetAddress,
                targetPort,
                agentConnectMessage,
                listOf()
            ))
            return
        }
        targetChannelContext.fireExceptionCaught(cause)
    }
}
