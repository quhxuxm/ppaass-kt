package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBody
import com.ppaass.kt.common.message.ProxyMessageBodyType
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory

internal class TargetToProxyHandler(private val proxyChannel: Channel,
                                    private val secureToken: String, private val messageId: String,
                                    private val targetAddress: String, private val targetPort: Int,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    companion object {
        private val logger = LoggerFactory.getLogger(TargetToProxyHandler::class.java)
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
        val proxyMessage = ProxyMessage(secureToken, MessageBodyEncryptionType.random(), proxyMessageBody)
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        this.proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener {
            if (!it.isSuccess) {
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