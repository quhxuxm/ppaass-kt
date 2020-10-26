package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
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
            throw PpaassException("No proxy channel context attach to target channel.")
        }
        val proxyChannel = proxyChannelContext.channel()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        if (agentConnectMessage == null) {
            throw PpaassException("No agent connect message attach to target channel.")
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
                    proxyChannelContext.close()
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
            logger.debug {
                "Recover auto read on proxy channel: ${
                    proxyChannelContext.channel().id().asLongText()
                }"
            }
            proxyChannel.read()
        } else {
            targetChannel.flush()
        }
    }

    override fun exceptionCaught(targetChannelContext: ChannelHandlerContext, cause: Throwable) {
        val targetChannel = targetChannelContext.channel()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        logger.error(cause) {
            "Exception happen on target channel ${
                targetChannelContext.channel().id().asLongText()
            }, remote address: ${
                targetChannelContext.channel().remoteAddress()
            }, targetAddress=${
                agentConnectMessage?.body?.targetAddress
            }, targetPort=${
                agentConnectMessage?.body?.targetPort
            }, targetConnectionType=${
                agentConnectMessage?.body?.bodyType
            }"
        }
        targetChannelContext.close()
    }
}
