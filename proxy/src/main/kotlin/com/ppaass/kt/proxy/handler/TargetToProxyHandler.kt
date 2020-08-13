package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import java.util.*

internal class TargetToProxyHandler(private val proxyChannel: Channel, private val messageId: String,
                                    private val targetAddress: String, private val targetPort: Int,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, messageId)
        with(proxyMessageBody) {
            targetAddress = this@TargetToProxyHandler.targetAddress
            targetPort = this@TargetToProxyHandler.targetPort
            originalData = originalDataByteArray
        }
        val proxyMessage =
                ProxyMessage(UUID.randomUUID().toString(), MessageBodyEncryptionType.random(), proxyMessageBody)
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        if (!this.proxyChannel.isActive) {
            logger.error("Fail to transfer data from target to proxy server because of proxy channel is not active.")
            throw PpaassException(
                    "Fail to transfer data from target to proxy server because of proxy channel is not active.")
        }
        this.proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener {
            if (!it.isSuccess) {
                this@TargetToProxyHandler.proxyChannel.close()
                targetChannelContext.close()
                logger.error("Fail to transfer data from target to proxy server.", it.cause())
                throw PpaassException("Fail to transfer data from target to proxy server.")
            }
            if (!proxyConfiguration.autoRead) {
                targetChannelContext.channel().read()
            }
        })
    }

    override fun channelReadComplete(targetChannelContext: ChannelHandlerContext) {
        this.proxyChannel.flush()
    }
}
