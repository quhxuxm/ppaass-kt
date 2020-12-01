package com.ppaass.agent.handler.socks

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessage
import com.ppaass.kt.common.AgentMessageBody
import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.generateUuid
import com.ppaass.kt.common.generateUuidInBytes
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.InetSocketAddress

private class SocksProxyTcpChannelConnectListener(
    private val agentChannel: Channel,
    private val socks5CommandRequest: Socks5CommandRequest,
    private val agentConfiguration: AgentConfiguration,
    private val socksV5ProxyBootstrap: Bootstrap) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes = 0

    override fun operationComplete(proxyChannelFuture: ChannelFuture) {
        if (!proxyChannelFuture.isSuccess) {
            if (failureTimes >=
                agentConfiguration.agentToProxyTcpChannelConnectRetry) {
                agentChannel.writeAndFlush(DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                    socks5CommandRequest.dstAddrType()))
                    .addListener(ChannelFutureListener.CLOSE)
                logger.error(
                    "Fail connect to proxy, agent channel = {}, target address = {}, target port = {}",
                    agentChannel.id().asLongText(), socks5CommandRequest.dstAddr(),
                    socks5CommandRequest.dstPort())
                return
            }
            failureTimes++
            logger.error(
                "Retry connect to proxy ({}), agent channel = {}, target address = {}, target port = {}",
                failureTimes,
                agentChannel.id().asLongText(), socks5CommandRequest.dstAddr(),
                socks5CommandRequest.dstPort())
            socksV5ProxyBootstrap
                .connect(agentConfiguration.proxyHost, agentConfiguration.proxyPort!!)
                .addListener(this
                )
            return
        }
        val proxyChannel = proxyChannelFuture.channel()
        val tcpConnectionInfo = SocksTcpConnectionInfo(
            agentTcpChannel = agentChannel,
            proxyTcpChannel = proxyChannel,
            targetHost = socks5CommandRequest.dstAddr(),
            targetPort = socks5CommandRequest.dstPort(),
            userToken = agentConfiguration.userToken!!,
            targetAddressType = socks5CommandRequest.dstAddrType()
        )
        proxyChannel.attr(SOCKS_TCP_CONNECTION_INFO)
            .setIfAbsent(tcpConnectionInfo)
        agentChannel.attr(SOCKS_TCP_CONNECTION_INFO)
            .setIfAbsent(tcpConnectionInfo)
        val agentMessageBody = AgentMessageBody(
            id = generateUuid(),
            bodyType = AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE,
            userToken = agentConfiguration.userToken!!,
            targetHost = tcpConnectionInfo.targetHost,
            targetPort = tcpConnectionInfo.targetPort,
            data = byteArrayOf())
        val agentMessage = AgentMessage(
            encryptionToken = generateUuidInBytes(),
            encryptionType = EncryptionType.choose(),
            body = agentMessageBody)
        val agentChannelPipeline = agentChannel.pipeline()
        try {
            agentChannelPipeline.remove(SocksProtocolHandler::class.java)
        } catch (e: java.lang.Exception) {
            logger.debug(
                "Fail to remove SocksV5Handler from proxy channel pipeline, proxy channel = {}",
                proxyChannel.id().asLongText())
        }
        proxyChannel.writeAndFlush(agentMessage)
            .addListener(ChannelFutureListener { proxyWriteChannelFuture: ChannelFuture ->
                if (proxyWriteChannelFuture.isSuccess) {
                    logger.debug {
                        "Success connect to target server, agent channel = ${
                            agentChannel.id().asLongText()
                        }, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }, target address = ${
                            tcpConnectionInfo.targetHost
                        }, target port = ${
                            tcpConnectionInfo.targetPort
                        }"
                    }
                    return@ChannelFutureListener
                }
                agentChannel.writeAndFlush(
                    DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        tcpConnectionInfo.targetAddressType))
                    .addListener(ChannelFutureListener.CLOSE)
                logger.error(proxyWriteChannelFuture.cause()) {
                    "Fail connect to target server because of exception, agent channel = ${
                        agentChannel.id().asLongText()
                    }, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target address = ${
                        tcpConnectionInfo.targetHost
                    }, target port = ${
                        tcpConnectionInfo.targetPort
                    }"
                }
            })
    }
}

@ChannelHandler.Sharable
@Service
internal class SocksProtocolHandler(
    private val socksProxyBootstrap: Bootstrap,
    private val agentConfiguration: AgentConfiguration,
    private val socksProxyUdpBootstrap: Bootstrap) :
    SimpleChannelInboundHandler<SocksMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext,
                              socksRequest: SocksMessage) {
        val agentChannel: Channel = agentChannelContext.channel()
        if (SocksVersion.UNKNOWN == socksRequest.version()) {
            logger.error {
                "Incoming protocol is unknown protocol, agent channel = ${
                    agentChannel.id().asLongText()
                }."
            }
            agentChannel.close()
            return
        }
        if (SocksVersion.SOCKS4a == socksRequest.version()) {
            logger.error {
                "Socks4a not support, agent channel = ${agentChannel.id().asLongText()}."
            }
            agentChannel.close()
            return
        }
        logger.debug {
            "Incoming request socks5, agent channel = ${
                agentChannel.id().asLongText()
            }."
        }
        val agentChannelPipeline: ChannelPipeline = agentChannelContext.pipeline()
        when (socksRequest) {
            is Socks5InitialRequest -> {
                logger.debug {
                    "Socks5 initial request coming always NO_AUTH, agent channel = ${
                        agentChannel.id().asLongText()
                    }"
                }
                agentChannelPipeline.addFirst(Socks5CommandRequestDecoder())
                agentChannelContext.writeAndFlush(
                    DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                return
            }
            is Socks5CommandRequest -> {
                val socks5CommandRequestType = socksRequest.type()
                logger.debug {
                    "Socks5 command request with ${socks5CommandRequestType} command coming, agent channel = ${
                        agentChannel.id().asLongText()
                    }"
                }
                when (socks5CommandRequestType) {
                    Socks5CommandType.CONNECT -> {
                        socksProxyBootstrap
                            .connect(agentConfiguration.proxyHost,
                                agentConfiguration.proxyPort!!)
                            .addListener(
                                SocksProxyTcpChannelConnectListener(
                                    agentChannel, socksRequest, this.agentConfiguration,
                                    socksProxyBootstrap))
                        return
                    }
                    Socks5CommandType.UDP_ASSOCIATE -> {
                        agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
                        this.bindUdpChannel { agentUdpChannelFuture: ChannelFuture ->
                            if (!agentUdpChannelFuture.isSuccess) {
                                logger.error(
                                    "Fail to associate UDP tunnel for agent channel because of exception, agent channel = {}",
                                    agentChannel.id().asLongText(),
                                    agentUdpChannelFuture.cause())
                                return@bindUdpChannel
                            }
                            val agentUdpChannel = agentUdpChannelFuture.channel()
                            val agentUdpAddress =
                                agentUdpChannelFuture.channel()
                                    .localAddress() as InetSocketAddress
                            val proxyTcpChannelForUdpTransfer: Channel = socksProxyBootstrap
                                .connect(this.agentConfiguration.proxyHost,
                                    this.agentConfiguration.proxyPort!!)
                                .syncUninterruptibly().channel()
                            val udpConnectionInfo = SocksUdpConnectionInfo(
                                agentUdpChannel = agentUdpChannel,
                                agentUdpPort = agentUdpAddress.port,
                                proxyTcpChannel = proxyTcpChannelForUdpTransfer,
                                agentTcpChannel = agentChannel,
                                clientSenderAssociateHost = socksRequest.dstAddr(),
                                clientSenderAssociatePort = socksRequest.dstPort(),
                                userToken = agentConfiguration.userToken!!
                            )
                            agentChannel.attr(SOCKS_UDP_CONNECTION_INFO)
                                .setIfAbsent(udpConnectionInfo)
                            proxyTcpChannelForUdpTransfer.attr(SOCKS_UDP_CONNECTION_INFO)
                                .setIfAbsent(udpConnectionInfo)
                            agentUdpChannel.attr(SOCKS_UDP_CONNECTION_INFO)
                                .setIfAbsent(udpConnectionInfo)
                            agentChannel.writeAndFlush(DefaultSocks5CommandResponse(
                                Socks5CommandStatus.SUCCESS,
                                Socks5AddressType.IPv4, LOCAL_IP_ADDRESS,
                                udpConnectionInfo.agentUdpPort))
                        }
                        return
                    }
                    else -> {
                        logger.error {
                            "${socks5CommandRequestType} socks5 request still not support, agent channel = ${
                                agentChannel.id().asLongText()
                            }"
                        }
                        agentChannel.close()
                        return
                    }
                }
            }
            else -> {
                logger.error {
                    "Wrong socks5 request, agent channel = ${
                        agentChannel.id().asLongText()
                    }, socks5 message:\n$socksRequest\n"
                }
                agentChannel.close()
            }
        }
    }

    private fun bindUdpChannel(udpBindListener: ChannelFutureListener) {
        try {
            socksProxyUdpBootstrap.bind(0).addListener(udpBindListener)
        } catch (e: Exception) {
            logger.error(e) {
                "Fail to bind udp channel because of exception."
            }
        }
    }

    private fun closeUdpChannel(socksUdpConnectionInfo: SocksUdpConnectionInfo) {
        val agentUdpChannelId = socksUdpConnectionInfo.agentUdpChannel.id().asLongText()
        try {
            socksUdpConnectionInfo.agentUdpChannel.close().syncUninterruptibly()
            logger.info(
                "Close agent udp channel, agent udp channel = $agentUdpChannelId.")
        } catch (e: Exception) {
            logger.error(e) {
                "Fail to stop agent udp channel because of exception, agent udp channel = ${agentUdpChannelId}."
            }
        }
    }

    override fun channelInactive(agentChannelContext: ChannelHandlerContext) {
        val agentChannel = agentChannelContext.channel()
        val udpConnectionInfo = agentChannel.attr(SOCKS_UDP_CONNECTION_INFO).get()
        if (udpConnectionInfo != null) {
            closeUdpChannel(udpConnectionInfo)
        }
        agentChannelContext.fireChannelInactive()
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable?) {
        val agentChannel = agentChannelContext.channel()
        logger.error(
            "Exception happen on agent channel, agent channel = {}", agentChannel.id().asLongText(),
            cause)
    }
}
