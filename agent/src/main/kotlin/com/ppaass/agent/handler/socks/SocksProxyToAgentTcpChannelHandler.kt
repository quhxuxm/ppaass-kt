package com.ppaass.agent.handler.socks

import com.ppaass.kt.common.Heartbeat
import com.ppaass.kt.common.JSON_OBJECT_MAPPER
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBodyType
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.NetUtil
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException

@ChannelHandler.Sharable
@Service
internal class SocksProxyToAgentTcpChannelHandler(
    private val socksAgentToProxyTcpChannelHandler: SocksAgentToProxyTcpChannelHandler
) : SimpleChannelInboundHandler<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              proxyMessage: ProxyMessage) {
        val proxyChannel = proxyChannelContext.channel()
        val tcpConnectionInfo = proxyChannel.attr(SOCKS_TCP_CONNECTION_INFO).get()
        if (tcpConnectionInfo == null && proxyMessage.body.bodyType != ProxyMessageBodyType.OK_UDP) {
            logger.error {
                "Fail to send proxy message to agent because of no connection information attached, proxy channel = ${
                    proxyChannel.id().asLongText()
                }"
            }
            proxyChannel.close()
            return
        }
        val agentTcpChannel = tcpConnectionInfo.agentTcpChannel
        when (proxyMessage.body.bodyType) {
            ProxyMessageBodyType.CONNECT_SUCCESS -> {
                agentTcpChannel.writeAndFlush(
                    DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                        tcpConnectionInfo.targetAddressType, tcpConnectionInfo.targetHost,
                        tcpConnectionInfo.targetPort)).addListener { agentChannelFuture ->
                    if (agentChannelFuture.isSuccess) {
                        agentTcpChannel.pipeline().addLast(socksAgentToProxyTcpChannelHandler)
                        return@addListener
                    }
                    val cause = agentChannelFuture.cause()
                    when (cause) {
                        is ClosedChannelException -> {
                            logger.error {
                                "Fail to write socks command response to client because of agent channel closed already, agent channel = ${
                                    agentTcpChannel.id().asLongText()
                                }, proxy channel = ${
                                    proxyChannel.id().asLongText()
                                }, target address = ${
                                    tcpConnectionInfo.targetHost
                                }, target port = ${
                                    tcpConnectionInfo.targetPort
                                }"
                            }
                            proxyChannel.close()
                            return@addListener
                        }
                        else -> {
                            logger.error(cause) {
                                "Fail to write socks command response to client because of exception, agent channel = ${
                                    agentTcpChannel.id().asLongText()
                                }, proxy channel = ${
                                    proxyChannel.id().asLongText()
                                }, target address = ${
                                    tcpConnectionInfo.targetHost
                                }, target port = ${
                                    tcpConnectionInfo.targetPort
                                }"
                            }
                            proxyChannel.close()
                            return@addListener
                        }
                    }
                }
                return
            }
            ProxyMessageBodyType.HEARTBEAT -> {
                val originalData = proxyMessage.body.data
                val heartbeat = JSON_OBJECT_MAPPER.readValue(originalData, Heartbeat::class.java)
                logger.debug {
                    "Discard proxy channel heartbeat, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, agent channel = ${
                        agentTcpChannel.id().asLongText()
                    }, heartbeat id = ${
                        heartbeat.id
                    } , heartbeat time = ${
                        heartbeat.utcDateTime
                    }."
                }
                agentTcpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener { agentChannelWriteFuture ->
                        if (agentChannelWriteFuture.isSuccess) {
                            return@addListener
                        }
                        agentTcpChannel.close()
                        proxyChannel.close()
                    }
                return
            }
            ProxyMessageBodyType.CONNECT_FAIL -> {
                logger.error {
                    "Fail connect to proxy, agent channel = ${
                        agentTcpChannel.id().asLongText()
                    }, target address = ${
                        tcpConnectionInfo.targetHost
                    }, target port = ${
                        tcpConnectionInfo.targetPort
                    }"
                }
                agentTcpChannel.writeAndFlush(
                    DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        tcpConnectionInfo.targetAddressType))
                    .addListener(ChannelFutureListener.CLOSE)
                return
            }
            ProxyMessageBodyType.OK_TCP -> {
                logger.debug("Write proxy message to agent: \n{}\n", proxyMessage)
                agentTcpChannel.writeAndFlush(
                    Unpooled.wrappedBuffer(proxyMessage.body.data))
                    .addListener { agentChannelFuture ->
                        if (agentChannelFuture.isSuccess) {
                            return@addListener
                        }
                        val cause = agentChannelFuture.cause()
                        when (cause) {
                            is ClosedChannelException -> {
                                logger.error {
                                    "Fail to transfer data from agent to client because of agent channel closed already, agent channel = ${
                                        agentTcpChannel.id().asLongText()
                                    }, proxy channel = ${
                                        proxyChannel.id().asLongText()
                                    }, target address = ${
                                        tcpConnectionInfo.targetHost
                                    }, target port = ${
                                        tcpConnectionInfo.targetPort
                                    }"
                                }
                                proxyChannel.close()
                                return@addListener
                            }
                            else -> {
                                logger.error(cause) {
                                    "Fail to transfer data from agent to client because of exception, agent channel = ${
                                        agentTcpChannel.id().asLongText()
                                    }, proxy channel = ${
                                        proxyChannel.id().asLongText()
                                    }, target address = ${
                                        tcpConnectionInfo.targetHost
                                    }, target port = ${
                                        tcpConnectionInfo.targetPort
                                    }."
                                }
                                proxyChannel.close()
                                return@addListener
                            }
                        }
                    }
                return
            }
            ProxyMessageBodyType.OK_UDP -> {
                logger.debug("Receive udp message: {}", proxyMessage)
                val udpConnectionInfo = proxyChannel.attr(SOCKS_UDP_CONNECTION_INFO).get()
                val recipient = InetSocketAddress(udpConnectionInfo.clientSenderHost!!,
                    udpConnectionInfo.clientSenderPort)
                val sender = InetSocketAddress(LOCAL_IP_ADDRESS,
                    udpConnectionInfo.agentUdpPort)
                val data = proxyMessage.body.data
                val socks5UdpResponseBuf = Unpooled.buffer()
                socks5UdpResponseBuf.writeByte(0)
                socks5UdpResponseBuf.writeByte(0)
                socks5UdpResponseBuf.writeByte(0)
                val clientRecipientHost = udpConnectionInfo.clientRecipientHost
                if (NetUtil.isValidIpV4Address(clientRecipientHost)) {
                    socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv4.byteValue().toInt())
                    Socks5AddressEncoder.DEFAULT
                        .encodeAddress(Socks5AddressType.IPv4, clientRecipientHost,
                            socks5UdpResponseBuf)
                } else {
                    if (NetUtil.isValidIpV6Address(clientRecipientHost)) {
                        socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv6.byteValue().toInt())
                        Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.IPv6, clientRecipientHost,
                                socks5UdpResponseBuf)
                    } else {
                        socks5UdpResponseBuf.writeByte(clientRecipientHost!!.length)
                        Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.DOMAIN,
                                clientRecipientHost,
                                socks5UdpResponseBuf)
                    }
                }
                socks5UdpResponseBuf.writeShort(udpConnectionInfo.clientRecipientPort)
                socks5UdpResponseBuf.writeBytes(data)
                logger.debug("Write udp message to client: \n{}\n",
                    ByteBufUtil.prettyHexDump(socks5UdpResponseBuf))
                val udpPackage = DatagramPacket(socks5UdpResponseBuf, recipient, sender)
                udpConnectionInfo.agentUdpChannel.writeAndFlush(udpPackage)
                return
            }
        }
    }
}
