package com.ppaass.proxy.handler

import com.ppaass.kt.common.AgentMessage
import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBody
import com.ppaass.kt.common.ProxyMessageBodyType
import com.ppaass.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException

private class WriteProxyTcpChannelDataToTargetListener(
    private val proxyTcpChannel: Channel,
    private val agentMessage: AgentMessage,
    private val proxyConfiguration: ProxyConfiguration
) : ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes: Int = 0;

    override fun operationComplete(targetChannelFuture: ChannelFuture) {
        val targetChannel = targetChannelFuture.channel()
        if (targetChannelFuture.isSuccess) {
            if (targetChannel.isWritable) {
                proxyTcpChannel.read();
            } else {
                targetChannel.flush();
            }
            return
        }
        val cause = targetChannelFuture.cause()
        if (cause is ClosedChannelException) {
            logger.error {
                "Fail to transfer data from proxy to target because of target channel closed, proxy channel = ${
                    proxyTcpChannel.id().asLongText()
                }, target channel = ${
                    targetChannel.id().asLongText()
                }, target address = ${
                    agentMessage.body.targetHost
                }, target port = ${
                    agentMessage.body.targetPort
                }, target connection type = ${
                    agentMessage.body.bodyType
                }."
            }
            proxyTcpChannel.close();
            return;
        }
        if (this.failureTimes >=
            this.proxyConfiguration.proxyTcpChannelToTargetTcpChannelRetry) {
            logger.error {
                "Fail to transfer data from proxy to target because of exception, proxy channel = ${
                    proxyTcpChannel.id().asLongText()
                }, target channel = ${
                    targetChannel.id().asLongText()
                }, target address = ${
                    agentMessage.body.targetHost
                }, target port = ${
                    agentMessage.body.targetPort
                }, target connection type = ${
                    agentMessage.body.bodyType
                }."
            }
            proxyTcpChannel.close();
            return;
        }
        failureTimes++;
        logger.error("Retry write to target (${
            failureTimes
        }), proxy channel = ${
            proxyTcpChannel.id().asLongText()
        }, target channel = ${
            targetChannel.id().asLongText()
        }");
        targetChannel
            .writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.data))
            .addListener(this);
        return;
    }
}

private class TargetConnectListener(
    private val proxyChannelContext: ChannelHandlerContext,
    private val agentMessage: AgentMessage,
    private val proxyConfiguration: ProxyConfiguration,
    private val targetBootstrap: Bootstrap) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes = 0

    override fun operationComplete(targetChannelFuture: ChannelFuture) {
        val proxyChannel = proxyChannelContext.channel()
        if (!targetChannelFuture.isSuccess) {
            val cause = targetChannelFuture.cause()
            if (cause is ClosedChannelException) {
                logger.error(cause) {
                    "Fail to connect target because of target channel closed, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }"
                }
                proxyChannel.close()
                return
            }
            if (failureTimes >=
                proxyConfiguration.proxyTcpChannelToTargetTcpChannelRetry) {
                logger.error(cause) {
                    "Fail to connect target because of exception, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }"
                }
                val proxyMessageBody = ProxyMessageBody(
                    bodyType = ProxyMessageBodyType.CONNECT_FAIL,
                    userToken = agentMessage.body.userToken,
                    targetHost = agentMessage.body.targetHost,
                    targetPort = agentMessage.body.targetPort,
                    data = byteArrayOf())
                val proxyMessage =
                    ProxyMessage(encryptionType = EncryptionType.choose(), body = proxyMessageBody)
                proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener.CLOSE)
                return
            }
            failureTimes++
            logger.error(cause) {
                "Retry connect to target (${
                    failureTimes
                }), proxy channel = ${
                    proxyChannel.id().asLongText()
                }"
            }
            targetBootstrap
                .connect(agentMessage.body.targetHost,
                    agentMessage.body.targetPort)
                .addListener(this)
            return
        }
        val targetChannel = targetChannelFuture.channel()
        val agentConnectionInfo = TcpConnectionInfo(
            targetHost = agentMessage.body.targetHost,
            targetPort = agentMessage.body.targetPort,
            userToken = agentMessage.body.userToken,
            proxyTcpChannel = proxyChannel,
            targetTcpChannel = targetChannel,
            targetTcpConnectionKeepAlive = agentMessage.body.bodyType == AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
        )
        targetChannel.attr(TCP_CONNECTION_INFO).setIfAbsent(agentConnectionInfo)
        proxyChannel.attr(TCP_CONNECTION_INFO).setIfAbsent(agentConnectionInfo)
        targetChannel.config()
            .setOption(ChannelOption.SO_KEEPALIVE, agentConnectionInfo.targetTcpConnectionKeepAlive)
        val proxyMessageBody =
            ProxyMessageBody(
                bodyType = ProxyMessageBodyType.CONNECT_SUCCESS,
                userToken = agentConnectionInfo.userToken,
                targetHost = agentConnectionInfo.targetHost,
                targetPort = agentConnectionInfo.targetPort,
                data = byteArrayOf())
        val proxyMessage =
            ProxyMessage(
                encryptionType = EncryptionType.choose(),
                body = proxyMessageBody)
        proxyChannel.writeAndFlush(proxyMessage)
            .addListener(WriteDataToProxyListener(targetChannel, proxyConfiguration, proxyMessage))
    }
}

@Service
@ChannelHandler.Sharable
internal class ProxyTcpChannelToTargetHandler(private val targetTcpBootstrap: Bootstrap,
                                              private val targetUdpBootstrap: Bootstrap,
                                              private val proxyConfiguration: ProxyConfiguration) :
    SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              agentMessage: AgentMessage) {
        val proxyChannel = proxyChannelContext.channel();
        val agentMessageBodyType = agentMessage.body.bodyType
        when (agentMessageBodyType) {
            AgentMessageBodyType.TCP_DATA -> {
                val agentTcpConnectionInfo =
                    proxyChannel.attr(TCP_CONNECTION_INFO).get();
                if (agentTcpConnectionInfo == null) {
                    logger.error {
                        "Fail to transfer data from proxy to target because of no target channel attached, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }."
                    }
                    proxyChannel.close();
                    return;
                }
                val targetTcpChannel = agentTcpConnectionInfo.targetTcpChannel
                targetTcpChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(agentMessage.body.data))
                    .addListener(
                        WriteProxyTcpChannelDataToTargetListener(proxyChannel, agentMessage,
                            proxyConfiguration));
            }
            AgentMessageBodyType.UDP_DATA -> {
                val recipient = InetSocketAddress(agentMessage.body.targetHost,
                    agentMessage.body.targetPort);
                val udpData = Unpooled.wrappedBuffer(agentMessage.body.data);
                val udpPackage = DatagramPacket(udpData, recipient);
                logger.debug("Agent message for udp: {}, data: \n{}\n", agentMessage,
                    ByteBufUtil.prettyHexDump(udpData));
                var udpConnectionInfo = proxyChannel.attr(UDP_CONNECTION_INFO).get();
                if (udpConnectionInfo == null) {
                    val targetUdpChannel =
                        targetUdpBootstrap.bind(0).sync().channel();
                    udpConnectionInfo = UdpConnectionInfo(
                        targetUdpChannel = targetUdpChannel,
                        proxyTcpChannel = proxyChannel,
                        targetHost = agentMessage.body.targetHost,
                        targetPort = agentMessage.body.targetPort,
                        userToken = agentMessage.body.userToken
                    );
                    targetUdpChannel.attr(UDP_CONNECTION_INFO).setIfAbsent(udpConnectionInfo)
                }
                logger.debug("Receive udp package from agent: {}", udpPackage);
                udpConnectionInfo.targetUdpChannel.writeAndFlush(udpPackage);
                logger.info {
                    "Ppaass udp proxy success to bind, local address: ${
                        udpConnectionInfo.targetUdpChannel.localAddress()
                    }, remote address: ${
                        udpConnectionInfo.targetUdpChannel.remoteAddress()
                    }"
                }
                return
            }
            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE, AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE -> {
                this.targetTcpBootstrap
                    .connect(agentMessage.body.targetHost,
                        agentMessage.body.targetPort)
                    .addListener(TargetConnectListener(proxyChannelContext, agentMessage,
                        proxyConfiguration,
                        this.targetTcpBootstrap));
                return
            }
            else -> {
                val agentTcpConnectionInfo =
                    proxyChannel.attr(TCP_CONNECTION_INFO).get();
                if (agentTcpConnectionInfo == null) {
                    return
                }
                logger.error {
                    "Fail to transfer data from proxy to target because of the body type is unknown, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target channel = ${
                        agentTcpConnectionInfo.targetTcpChannel.id().asLongText()
                    }, body type = ${
                        agentMessageBodyType
                    }"
                };
                proxyChannel.close();
                agentTcpConnectionInfo.targetTcpChannel.close();
            }
        }
    }
}
