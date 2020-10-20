package com.ppaass.kt.agent.handler.socks.v5

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ProtocolHandler(
    private val socksV5ConnectCommandHandler: SocksV5ConnectCommandHandler
) :
    SimpleChannelInboundHandler<SocksMessage>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socksRequest: SocksMessage) {
        val channelPipeline = agentChannelContext.pipeline()
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        with(channelPipeline) {
            when (socksRequest) {
                is Socks5InitialRequest -> {
                    logger.debug(
                        "Socks5 initial request coming always NO_AUTH ...")
                    addBefore(SocksV5ProtocolHandler::class.java.name, Socks5CommandRequestDecoder::class.java.name,
                        Socks5CommandRequestDecoder())
                    agentChannelContext.writeAndFlush(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    return@channelRead0
                }
                is Socks5CommandRequest -> {
                    logger.debug(
                        "Socks5 command request with {} command coming ...", socksRequest.type())
                    when (socksRequest.type()) {
                        Socks5CommandType.CONNECT -> {
                            addLast(SocksV5ConnectCommandHandler::class.java.name, socksV5ConnectCommandHandler)
                            agentChannelContext.fireChannelRead(socksRequest)
                            return@channelRead0
                        }
                        Socks5CommandType.BIND -> {
                            logger.error(
                                "BIND socks5 request still not support, clientChannelId={}",
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                        Socks5CommandType.UDP_ASSOCIATE -> {
                            logger.error(
                                "UDP_ASSOCIATE socks5 request still not support, clientChannelId={}",
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                        else -> {
                            logger.error(
                                "Unknown command type[{}] clientChannelId={}", socksRequest.type(),
                                clientChannelId)
                            remove(this@SocksV5ProtocolHandler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                    }
                }
                else -> {
                    logger.error(
                        "Current request type of socks5 still do not support, socks5 message: {}", socksRequest)
                    remove(this@SocksV5ProtocolHandler)
                    agentChannelContext.close()
                    return@channelRead0
                }
            }
        }
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}
