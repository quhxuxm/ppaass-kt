package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.discardProxyHeartbeatHandler
import com.ppaass.kt.agent.handler.lengthFieldPrepender
import com.ppaass.kt.agent.handler.resourceClearHandler
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SocksV5ConnectCommandHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<Socks5CommandRequest>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val proxyBootstrap: Bootstrap

    init {
        this.proxyBootstrap = Bootstrap()
        with(this.proxyBootstrap) {
            group(NioEventLoopGroup(
                    agentConfiguration.staticAgentConfiguration.proxyIoEventThreadNumber))
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_RCVBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)
        }
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socks5CommandRequest: Socks5CommandRequest) {
        proxyBootstrap.handler(object : ChannelInitializer<Channel>() {
            override fun initChannel(proxyChannel: Channel) {
                with(proxyChannel.pipeline()) {
                    addLast(Lz4FrameDecoder())
                    addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                            0, 4, 0,
                            4))
                    addLast(ProxyMessageDecoder(
                            agentPrivateKeyString = agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                    addLast(discardProxyHeartbeatHandler)
                    addLast(
                            SocksV5ProxyToAgentHandler(
                                    agentChannel = agentChannelContext.channel(),
                                    agentConfiguration = agentConfiguration,
                                    socks5CommandRequest = socks5CommandRequest))
                    addLast(resourceClearHandler)
                    addLast(Lz4FrameEncoder())
                    addLast(lengthFieldPrepender)
                    addLast(AgentMessageEncoder(
                            proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                }
            }
        })
        proxyBootstrap.connect(agentConfiguration.proxyAddress, agentConfiguration.proxyPort).addListener(
                ChannelFutureListener { proxyConnectionFuture: ChannelFuture ->
                    if (!proxyConnectionFuture.isSuccess) {
                        // Close the connection if the connection attempt has failed.
                        logger.debug(
                                "Fail connect to: {}:{}", socks5CommandRequest.dstAddr(),
                                socks5CommandRequest.dstPort())
                        agentChannelContext.channel().writeAndFlush(
                                DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                        socks5CommandRequest.dstAddrType()))
                                .addListener(ChannelFutureListener.CLOSE)
                    }

                    val proxyChannel = proxyConnectionFuture.channel()
                    logger.debug(
                            "Success connect to target server: {}:{}", socks5CommandRequest.dstAddr(),
                            socks5CommandRequest.dstPort())
                    agentChannelContext.pipeline().apply {
                        addLast(
                                SocksV5AgentToProxyHandler(proxyChannel,
                                        socks5CommandRequest, agentConfiguration))
                        addLast(resourceClearHandler)
                        if (this[SocksV5ConnectCommandHandler::class.java.name] != null) {
                            remove(SocksV5ConnectCommandHandler::class.java.name)
                        }
                    }
                    agentChannelContext.channel().writeAndFlush(DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS,
                            socks5CommandRequest.dstAddrType(),
                            socks5CommandRequest.dstAddr(),
                            socks5CommandRequest.dstPort()))
                })
    }
}
