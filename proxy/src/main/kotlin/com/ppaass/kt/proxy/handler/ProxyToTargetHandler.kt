package com.ppaass.kt.proxy.handler

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
                    logger.error {
                        "Fail to transfer data from proxy to target because of no target channel attached, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }, agent message: \n${
                            agentMessage
                        }.\n"
                    }
                    proxyChannelContext.close()
                    return
                }
                targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                    .addListener {
                        if (!it.isSuccess) {
                            logger.error(it.cause()) {
                                "Fail to transfer data from proxy to target because of exception, proxy channel = ${
                                    proxyChannel.id().asLongText()
                                }, target channel = ${
                                    targetChannel.id().asLongText()
                                }, agent message: \n${
                                    agentMessage
                                }.\n"
                            }
                        }
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
                        "Fail to connect to target because of no target address, proxy channel = ${
                            proxyChannelContext.channel().id().asLongText()
                        }, agent message = \n${
                            agentMessage
                        }.\n")
                    proxyChannelContext.close()
                    return
                }
                if (targetPort == null) {
                    logger.error(
                        "Fail to connect to target because of no target port, proxy channel = ${
                            proxyChannelContext.channel().id().asLongText()
                        }, agent message = \n${
                            agentMessage
                        }.\n")
                    proxyChannelContext.close()
                    return
                }
                logger.debug(
                    "Begin to connect ${targetAddress}:${targetPort}, proxy channel = ${
                        proxyChannelContext.channel().id().asLongText()
                    }, agent message = \n${
                        agentMessage
                    }.\n")
                val targetConnectFuture = this.targetBootstrap.connect(targetAddress, targetPort)
                targetConnectFuture
                    .addListener((ChannelFutureListener { targetChannelFuture ->
                        val targetChannel = targetChannelFuture.channel()
                        val proxyChannel = proxyChannelContext.channel()
                        if (!targetChannelFuture.isSuccess) {
                            logger.error(targetChannelFuture.cause()) {
                                "Fail to connect to target because of exception, proxy channel = ${
                                    proxyChannelContext.channel().id().asLongText()
                                }, agent message = \n${
                                    agentMessage
                                }.\n"
                            }
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
                                "Fail to connect to target because of incorrect body type, proxy channel = ${
                                    proxyChannelContext.channel().id().asLongText()
                                }, target channel = ${
                                    targetChannel.id().asLongText()
                                }, agent message = \n${
                                    agentMessage
                                }.\n"
                                proxyChannelContext.close()
                                targetChannel.close()
                                return@ChannelFutureListener
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
            "Exception happen on proxy channel, proxy channel = ${
                proxyChannelContext.channel().id().asLongText()
            }, target channel = ${
                targetChannel?.id()?.asLongText()
            }, remote address = ${
                proxyChannelContext.channel().remoteAddress()
            }, target address = ${
                agentConnectMessage?.body?.targetAddress
            }, target port = ${
                agentConnectMessage?.body?.targetPort
            }, target connection type = ${
                agentConnectMessage?.body?.bodyType
            }"
        }
    }
}
