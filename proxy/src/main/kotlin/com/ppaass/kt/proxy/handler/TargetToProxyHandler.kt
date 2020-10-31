package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetToProxyHandler(
) : SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        if (proxyChannelContext == null) {
            logger.error {
                "Fail to activate target channel because of no proxy channel context attached, target channel = ${
                    targetChannel.id().asLongText()
                }."
            }
            return
        }
        val proxyChannel = proxyChannelContext.channel()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        if (agentConnectMessage == null) {
            logger.error {
                "Fail to activate target channel because of no agent connect message attached, target channel = ${
                    targetChannel.id().asLongText()
                }."
            }
            return
        }
        val proxyMessageBody =
            ProxyMessageBody(ProxyMessageBodyType.CONNECT_SUCCESS, generateUid())
        proxyMessageBody.targetAddress = agentConnectMessage.body.targetAddress
        proxyMessageBody.targetPort = agentConnectMessage.body.targetPort
        val successMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(),
                proxyMessageBody)
        proxyChannel.writeAndFlush(successMessage)
            .addListener((ChannelFutureListener { proxyChannelFuture ->
                if (!proxyChannelFuture.isSuccess) {
                    logger.error(proxyChannelFuture.cause()) {
                        "Fail to response CONNECT_SUCCESS data from proxy to agent because of exception, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }, target channel = ${
                            targetChannel.id().asLongText()
                        }."
                    }
                    targetChannel.close()
                    return@ChannelFutureListener
                }
                if (proxyChannel.isWritable) {
                    targetChannel.read()
                } else {
                    proxyChannel.flush()
                }
            }))
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
        proxyChannel.writeAndFlush(proxyMessage).addListener {
            if (!it.isSuccess) {
                logger.error(it.cause()) {
                    "Fail to transfer data from target to proxy because of exception, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target channel = ${
                        targetChannel.id().asLongText()
                    }."
                }
            }
            if (proxyChannel.isWritable) {
                targetChannel.read()
            } else {
                proxyChannel.flush()
            }
        }
    }

    override fun channelWritabilityChanged(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val proxyChannel = proxyChannelContext.channel()
        if (targetChannel.isWritable) {
            proxyChannel.read()
        } else {
            targetChannel.flush()
        }
    }

    override fun exceptionCaught(targetChannelContext: ChannelHandlerContext, cause: Throwable) {
        val targetChannel = targetChannelContext.channel()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        logger.error(cause) {
            "Exception happen on target channel, proxy channel = ${
                proxyChannelContext?.channel()?.id()?.asLongText()
            },target channel = ${
                targetChannelContext.channel().id().asLongText()
            }, remote address = ${
                targetChannelContext.channel().remoteAddress()
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
