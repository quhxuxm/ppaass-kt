package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ProtocolHandler(
    private val agentConfiguration: AgentConfiguration,
    private val socksV5ProxyServerBootstrap: Bootstrap
) :
    SimpleChannelInboundHandler<SocksMessage>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext,
                              socksRequest: SocksMessage) {
        val channelPipeline = agentChannelContext.pipeline()
        val agentChannel = agentChannelContext.channel()
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        with(channelPipeline) {
            when (socksRequest) {
                is Socks5InitialRequest -> {
                    logger.debug(
                        "Socks5 initial request coming always NO_AUTH ...")
                    addBefore(SocksV5ProtocolHandler::class.java.name,
                        Socks5CommandRequestDecoder::class.java.name,
                        Socks5CommandRequestDecoder())
                    agentChannelContext.writeAndFlush(
                        DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    return
                }
                is Socks5CommandRequest -> {
                    logger.debug {
                        "Socks5 command request with ${
                            socksRequest.type()
                        } command coming, channel id =${
                            agentChannelContext.channel().id().asLongText()
                        }, targetAddress=${
                            socksRequest.dstAddr()
                        }, targetPort=${
                            socksRequest.dstPort()
                        }"
                    }
                    when (socksRequest.type()) {
                        Socks5CommandType.CONNECT -> {
                            socksV5ProxyServerBootstrap.connect(agentConfiguration.proxyAddress,
                                agentConfiguration.proxyPort)
                                .addListener((ChannelFutureListener { proxyChannelFuture ->
                                    val proxyChannel = proxyChannelFuture.channel()
                                    if (!proxyChannelFuture.isSuccess) {
                                        proxyChannel.close()
                                        agentChannel.writeAndFlush(
                                            DefaultSocks5CommandResponse(
                                                Socks5CommandStatus.FAILURE,
                                                socksRequest.dstAddrType()))
                                            .addListener(ChannelFutureListener.CLOSE)
                                        logger.error {
                                            "Fail to connect proxy, agent channel id =${
                                                agentChannelContext.channel().id().asLongText()
                                            }, targetAddress=${
                                                socksRequest.dstAddr()
                                            }, targetPort=${
                                                socksRequest.dstPort()
                                            }"
                                        }
                                        return@ChannelFutureListener
                                    }
                                    proxyChannel.attr(AGENT_CHANNEL_CONTEXT)
                                        .setIfAbsent(agentChannelContext)
                                    proxyChannel.attr(SOCKS_V5_COMMAND_REQUEST)
                                        .setIfAbsent(socksRequest)
                                    proxyChannel.attr(HANDLERS_TO_REMOVE_AFTER_PROXY_ACTIVE)
                                        .setIfAbsent(listOf(this@SocksV5ProtocolHandler))
                                }))
                            return
                        }
                        Socks5CommandType.BIND -> {
                            logger.error(
                                "BIND socks5 request still not support, clientChannelId={}",
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return
                        }
                        Socks5CommandType.UDP_ASSOCIATE -> {
                            logger.error(
                                "UDP_ASSOCIATE socks5 request still not support, clientChannelId={}",
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return
                        }
                        else -> {
                            logger.error(
                                "Unknown command type[{}] clientChannelId={}", socksRequest.type(),
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return
                        }
                    }
                }
                else -> {
                    logger.error(
                        "Current request type of socks5 still do not support, socks5 message: {}",
                        socksRequest)
                    remove(this@SocksV5ProtocolHandler)
                    agentChannelContext.close()
                    return
                }
            }
        }
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}
