package com.ppaass.proxy.handler;

import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBody
import com.ppaass.kt.common.ProxyMessageBodyType
import com.ppaass.proxy.ProxyConfiguration
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetTcpChannelToProxyHandler(private val proxyConfiguration: ProxyConfiguration) :
    SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext,
                              targetOriginalMessageBuf: ByteBuf) {
        val targetChannel = targetChannelContext.channel()
        val agentTcpConnectionInfo = targetChannel.attr(TCP_CONNECTION_INFO).get()
        agentTcpConnectionInfo?.let {
            val proxyTcpChannel = agentTcpConnectionInfo.proxyTcpChannel
            val originalDataByteArray = ByteArray(targetOriginalMessageBuf.readableBytes())
            targetOriginalMessageBuf.readBytes(originalDataByteArray);
            val proxyMessageBody =
                ProxyMessageBody(
                    bodyType = ProxyMessageBodyType.OK_TCP,
                    userToken = agentTcpConnectionInfo.userToken,
                    targetHost = agentTcpConnectionInfo.targetHost,
                    targetPort = agentTcpConnectionInfo.targetPort,
                    data =
                    originalDataByteArray);
            val proxyMessage = ProxyMessage(
                encryptionType = EncryptionType.choose(),
                body = proxyMessageBody);
            proxyTcpChannel.writeAndFlush(proxyMessage)
                .addListener(WriteDataToProxyListener(targetChannel, proxyConfiguration,
                    proxyMessage));
            return
        }
        logger.error {
            "Fail to transfer data from target to proxy because of no agent connection information attached, target channel = ${
                targetChannel.id().asLongText()
            }."
        }
        targetChannel.close()
        return;
    }

    override fun channelReadComplete(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel();
        val agentTcpConnectionInfo = targetChannel.attr(TCP_CONNECTION_INFO).get();
        agentTcpConnectionInfo?.let {
            val proxyChannel = agentTcpConnectionInfo?.proxyTcpChannel
            if (proxyChannel.isWritable) {
                targetChannel.read();
            } else {
                proxyChannel.flush();
            }
            return
        }
        targetChannel.read();
    }

    override fun channelWritabilityChanged(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val agentTcpConnectionInfo = targetChannel.attr(TCP_CONNECTION_INFO).get()
        if (targetChannel.isWritable) {
            agentTcpConnectionInfo?.proxyTcpChannel?.read()
        } else {
            targetChannel.flush()
        }
    }

    override fun exceptionCaught(targetChannelContext: ChannelHandlerContext, cause: Throwable) {
        val targetChannel = targetChannelContext.channel();
        val agentTcpConnectionInfo = targetChannel.attr(TCP_CONNECTION_INFO).get();
        val proxyChannel = agentTcpConnectionInfo?.proxyTcpChannel
        logger.error(cause) {
            "Exception happen on target channel, proxy channel = ${
                proxyChannel?.id()?.asLongText()
            },target channel = ${
                targetChannel.id().asLongText()
            }, target address = ${
                agentTcpConnectionInfo?.targetHost
            }, target port = ${
                agentTcpConnectionInfo?.targetHost
            }."
        }
    }
}
