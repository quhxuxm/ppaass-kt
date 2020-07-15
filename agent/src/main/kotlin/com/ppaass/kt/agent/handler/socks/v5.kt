package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
class SocksV5ConnectionHandler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(SocksV5ConnectionHandler::class.java)
    }

    override fun channelRead(agentChannelContext: ChannelHandlerContext, socksRequest: Any) {
        val channelPipeline = agentChannelContext.pipeline()
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        with(channelPipeline) {
            when (socksRequest) {
                is Socks5InitialRequest -> {
                    logger.debug(
                            "Socks5 initial request coming always NO_AUTH ...")
                    addBefore(SocksV5ConnectionHandler::class.java.name, Socks5CommandRequestDecoder::class.java.name,
                            Socks5CommandRequestDecoder())
                    agentChannelContext.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    return@channelRead
                }
                is Socks5CommandRequest -> {
                    logger.debug(
                            "Socks5 command request with {} command coming ...", socksRequest.type())
                    when (socksRequest.type()) {
                        Socks5CommandType.CONNECT -> {
                            TODO("connection handler")
                            //  addLast(this.socks5SetupAgentToProxyConnectionHandler)
                            agentChannelContext.fireChannelRead(socksRequest)
                            return@channelRead
                        }
                        Socks5CommandType.BIND -> {
                            logger.error(
                                    "BIND socks5 request still not support, clientChannelId={}",
                                    clientChannelId)
                            remove(this@SocksV5ConnectionHandler)
                            agentChannelContext.close()
                            return@channelRead
                        }
                        Socks5CommandType.UDP_ASSOCIATE -> {
                            logger.error(
                                    "UDP_ASSOCIATE socks5 request still not support, clientChannelId={}",
                                    clientChannelId)
                            remove(this@SocksV5ConnectionHandler)
                            agentChannelContext.close()
                            return@channelRead
                        }
                        else -> {
                            logger.error(
                                    "Unknown command type[{}] clientChannelId={}", socksRequest.type(),
                                    clientChannelId)
                            remove(this@SocksV5ConnectionHandler)
                            agentChannelContext.close()
                            return@channelRead
                        }
                    }
                }
                else -> {
                    logger.error(
                            "Current request type of socks5 still do not support, socks5 message: {}", socksRequest)
                    remove(this@SocksV5ConnectionHandler)
                    agentChannelContext.close()
                    return@channelRead
                }
            }
        }
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }
}