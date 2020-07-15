package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
class SocksV5Handler(private val agentConfiguration: AgentConfiguration) : SimpleChannelInboundHandler<SocksMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(
                SocksV5Handler::class.java)
    }

    private val socksV5ConnectHandler = SocksV5ConnectCommandHandler(this.agentConfiguration)

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socksRequest: SocksMessage) {
        val channelPipeline = agentChannelContext.pipeline()
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        with(channelPipeline) {
            when (socksRequest) {
                is Socks5InitialRequest -> {
                    logger.debug(
                            "Socks5 initial request coming always NO_AUTH ...")
                    addBefore(SocksV5Handler::class.java.name, Socks5CommandRequestDecoder::class.java.name,
                            Socks5CommandRequestDecoder())
                    agentChannelContext.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    return@channelRead0
                }
                is Socks5CommandRequest -> {
                    logger.debug(
                            "Socks5 command request with {} command coming ...", socksRequest.type())
                    when (socksRequest.type()) {
                        Socks5CommandType.CONNECT -> {
                            addLast(this@SocksV5Handler.socksV5ConnectHandler)
                            agentChannelContext.fireChannelRead(socksRequest)
                            return@channelRead0
                        }
                        Socks5CommandType.BIND -> {
                            logger.error(
                                    "BIND socks5 request still not support, clientChannelId={}",
                                    clientChannelId)
                            remove(this@SocksV5Handler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                        Socks5CommandType.UDP_ASSOCIATE -> {
                            logger.error(
                                    "UDP_ASSOCIATE socks5 request still not support, clientChannelId={}",
                                    clientChannelId)
                            remove(this@SocksV5Handler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                        else -> {
                            logger.error(
                                    "Unknown command type[{}] clientChannelId={}", socksRequest.type(),
                                    clientChannelId)
                            remove(this@SocksV5Handler)
                            agentChannelContext.close()
                            return@channelRead0
                        }
                    }
                }
                else -> {
                    logger.error(
                            "Current request type of socks5 still do not support, socks5 message: {}", socksRequest)
                    remove(this@SocksV5Handler)
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