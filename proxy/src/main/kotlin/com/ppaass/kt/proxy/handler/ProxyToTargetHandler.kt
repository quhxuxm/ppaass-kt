package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class ProxyToTargetHandler(private val targetBootstrap: Bootstrap) :
    SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        proxyChannelContext.channel().read()
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              agentMessage: AgentMessage) {
        val bodyType = agentMessage.body.bodyType
        when (bodyType) {
            AgentMessageBodyType.DATA -> {
                val proxyChannel = proxyChannelContext.channel();
                val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
                if (targetChannel == null) {
                    logger.error { "Fail to transfer data from proxy to target because of no target channel attached, agent message: ${agentMessage}" }
                    proxyChannelContext.close()
                    return
                }
                targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                    .addListener {
                        if (targetChannel.isWritable) {
                            proxyChannel.read()
                        } else {
                            targetChannel.flush()
                        }
                    }
                return
            }
            else -> {
                val targetAddress = agentMessage.body.targetAddress
                val targetPort = agentMessage.body.targetPort
                if (targetAddress == null) {
                    logger.error(
                        "Return because of targetAddress is null, message id=${agentMessage.body.id}")
                    throw PpaassException(
                        "Return because of targetAddress is null, message id=${agentMessage.body.id}")
                }
                if (targetPort == null) {
                    logger.error(
                        "Return because of targetPort is null, message id=${agentMessage.body.id}")
                    throw PpaassException(
                        "Return because of targetPort is null, message id=${agentMessage.body.id}")
                }
                logger.debug(
                    "Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
                val targetConnectFuture = this.targetBootstrap.connect(targetAddress, targetPort)
                targetConnectFuture
                    .addListener((ChannelFutureListener { targetChannelFuture ->
                        val targetChannel = targetChannelFuture.channel()
                        val proxyChannel = proxyChannelContext.channel()
                        if (!targetChannelFuture.isSuccess) {
                            logger.error(
                                "Fail connect to ${targetAddress}:${targetPort}.",
                                targetChannelFuture.cause())
                            val proxyMessageBody =
                                ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL,
                                    agentMessage.body.id)
                            proxyMessageBody.targetAddress = agentMessage.body.targetAddress
                            proxyMessageBody.targetPort = agentMessage.body.targetPort
                            val failProxyMessage =
                                ProxyMessage(generateUid(), MessageBodyEncryptionType.random(),
                                    proxyMessageBody)
                            proxyChannel.writeAndFlush(failProxyMessage)
                                .addListener(ChannelFutureListener.CLOSE)
                            targetChannel.close()
                            return@ChannelFutureListener
                        }
                        when (agentMessage.body.bodyType) {
                            AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE -> {
                                if (targetChannel.isOpen) {
                                    targetChannel.config()
                                        .setOption(ChannelOption.SO_KEEPALIVE, false)
                                }
                                if (proxyChannel.isOpen) {
                                    proxyChannel.config()
                                        .setOption(ChannelOption.SO_KEEPALIVE, false)
                                }
                            }
                            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE -> {
                                if (targetChannel.isOpen) {
                                    targetChannel.config()
                                        .setOption(ChannelOption.SO_KEEPALIVE, true)
                                }
                                if (proxyChannel.isOpen) {
                                    proxyChannel.config()
                                        .setOption(ChannelOption.SO_KEEPALIVE, true)
                                }
                            }
                            else -> {
                                throw PpaassException(
                                    "A wrong body type should not happen for setup connection.")
                            }
                        }
                        targetChannel.attr(PROXY_CHANNEL_CONTEXT).setIfAbsent(proxyChannelContext)
                        proxyChannel.attr(TARGET_CHANNEL).setIfAbsent(targetChannel)
                        targetChannel.attr(AGENT_CONNECT_MESSAGE).setIfAbsent(agentMessage)
                        proxyChannel.read()
                    }))
            }
        }
    }

    override fun channelWritabilityChanged(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
        if (proxyChannel.isWritable) {
            logger.debug {
                "Recover auto read on target channel: ${
                    targetChannel.id().asLongText()
                }"
            }
            targetChannel.read()
        } else {
            proxyChannel.flush()
        }
    }

    override fun exceptionCaught(proxyChannelContext: ChannelHandlerContext, cause: Throwable) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
        val agentConnectMessage = targetChannel?.attr(AGENT_CONNECT_MESSAGE)?.get()
        logger.error(cause) {
            "Exception happen on proxy channel ${
                proxyChannelContext.channel().id().asLongText()
            }, remote address: ${
                proxyChannelContext.channel().remoteAddress()
            }, targetAddress=${
                agentConnectMessage?.body?.targetAddress
            }, targetPort=${
                agentConnectMessage?.body?.targetPort
            }, targetConnectionType=${
                agentConnectMessage?.body?.bodyType
            }"
        }
    }
}
