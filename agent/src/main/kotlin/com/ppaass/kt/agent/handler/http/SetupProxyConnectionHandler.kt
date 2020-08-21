package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.discardProxyHeartbeatHandler
import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.agent.handler.lengthFieldPrepender
import com.ppaass.kt.agent.handler.resourceClearHandler
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SetupProxyConnectionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<Any>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val proxyServerBootstrapIoEventLoopGroup = NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.dataTransferIoEventThreadNumber)

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        logger.debug("Agent receive a client connection, clientChannelId={}", clientChannelId)
        if (msg !is FullHttpRequest) {
            //A https request to send data
            logger.debug("Incoming request is https protocol to send data, clientChannelId={}", clientChannelId)
            val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
            if (channelCacheInfo == null) {
                logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
                throw PpaassException("Fail to find channel cache information, clientChannelId=$clientChannelId")
            }
            if (!channelCacheInfo.proxyChannel.isActive) {
                logger.error("Fail to write data to proxy channel because of it is inactive, clientChannelId={}", clientChannelId)
                throw PpaassException("Fail to write data to proxy channel because of it is inactive, clientChannelId=$clientChannelId")
            }
            writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    channelCacheInfo.proxyChannel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                    msg, clientChannelId, MessageBodyEncryptionType.random())
            return
        }
        if (HttpMethod.CONNECT === msg.method()) {
            //A https request to setup the connection
            logger.debug("Incoming request is https protocol to setup connection, clientChannelId={}", clientChannelId)
            val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
            val proxyBootstrapForHttps = createProxyBootstrapForHttps(agentChannelContext, httpConnectionInfo, clientChannelId) {
                val okResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                agentChannelContext.channel().writeAndFlush(okResponse)
                        .addListener(ChannelFutureListener { okResponseFuture ->
                            okResponseFuture.channel().pipeline().apply {
                                if (this[HttpServerCodec::class.java.name] != null) {
                                    remove(HttpServerCodec::class.java.name)
                                }
                                if (this[HttpObjectAggregator::class.java.name] != null) {
                                    remove(HttpObjectAggregator::class.java.name)
                                }
                            }
                        })
            }
            proxyBootstrapForHttps.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).addListener {
                if (!it.isSuccess) {
                    agentChannelContext.close()
                    logger.error("Fail to connect to proxy server because of exception.",
                            it.cause())
                    throw PpaassException("Fail to connect to proxy server because of exception.", it.cause())
                }
            }
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        ReferenceCountUtil.retain(msg, 1)
        val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
        if (channelCacheInfo != null) {
            if (channelCacheInfo.proxyChannel.isActive) {
                writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                        channelCacheInfo.proxyChannel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                        msg, clientChannelId, MessageBodyEncryptionType.random()) {
                    if (!it.isSuccess) {
                        ChannelInfoCache.removeChannelInfo(clientChannelId)
                        agentChannelContext.close()
                        channelCacheInfo.proxyChannel.close()
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, " +
                                        "targetHost=${channelCacheInfo.targetHost}, targetPort =${channelCacheInfo.targetPort}",
                                it.cause())
                    }
                }
                return
            }
        }
        val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
        val proxyBootstrap = createProxyBootstrapForHttp(agentChannelContext, httpConnectionInfo, clientChannelId) { proxyChannelContext ->
            writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    proxyChannelContext.channel(), httpConnectionInfo.host, httpConnectionInfo.port,
                    msg, clientChannelId, MessageBodyEncryptionType.random()) {
                if (!it.isSuccess) {
                    ChannelInfoCache.removeChannelInfo(clientChannelId)
                    agentChannelContext.close()
                    proxyChannelContext.close()
                    throw PpaassException(
                            "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, " +
                                    "targetHost=${httpConnectionInfo.host}, targetPort =${httpConnectionInfo.port}",
                            it.cause())
                }
            }
        }
        proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).addListener(ChannelFutureListener {
            if (!it.isSuccess) {
                ChannelInfoCache.removeChannelInfo(clientChannelId)
                agentChannelContext.close()
                it.channel().close()
                logger.error("Fail to connect to proxy server because of exception.",
                        it.cause())
                throw PpaassException("Fail to connect to proxy server because of exception.", it.cause())
            }
        })
    }

    private fun createProxyBootstrapForHttp(agentChannelContext: ChannelHandlerContext,
                                            httpConnectionInfo: HttpConnectionInfo,
                                            clientChannelId: String,
                                            initOnChannelActivate: (proxyChannelContext: ChannelHandlerContext) -> Unit = {}): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyServerBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_RCVBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)
        }
        proxyBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(httpProxyChannel: SocketChannel) {
                httpProxyChannel.pipeline().apply {
                    addLast(Lz4FrameDecoder())
                    addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                    addLast(ProxyMessageDecoder(
                            agentPrivateKeyString = agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                    addLast(discardProxyHeartbeatHandler)
                    addLast(ExtractProxyMessageOriginalDataDecoder())
                    addLast(HttpResponseDecoder())
                    addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                    addLast(proxyServerBootstrapIoEventLoopGroup,
                            TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                    httpConnectionInfo.host, httpConnectionInfo.port,
                                    clientChannelId,
                                    agentConfiguration, initOnChannelActivate))
                    addLast(resourceClearHandler)
                    addLast(Lz4FrameEncoder())
                    addLast(lengthFieldPrepender)
                    addLast(AgentMessageEncoder(
                            proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                }
            }
        })
        return proxyBootstrap
    }

    private fun createProxyBootstrapForHttps(agentChannelContext: ChannelHandlerContext,
                                             httpConnectionInfo: HttpConnectionInfo,
                                             clientChannelId: String,
                                             initOnChannelActivate: (proxyChannelContext: ChannelHandlerContext) -> Unit = {}): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyServerBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_RCVBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpsProxyChannel: SocketChannel) {
                    with(httpsProxyChannel.pipeline()) {
                        addLast(Lz4FrameDecoder())
                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                                0, 4, 0,
                                4))
                        addLast(ProxyMessageDecoder(
                                agentPrivateKeyString = agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                        addLast(discardProxyHeartbeatHandler)
                        addLast(ExtractProxyMessageOriginalDataDecoder())
                        addLast(proxyServerBootstrapIoEventLoopGroup,
                                TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                        httpConnectionInfo.host, httpConnectionInfo.port,
                                        clientChannelId,
                                        agentConfiguration, initOnChannelActivate))
                        addLast(resourceClearHandler)
                        addLast(Lz4FrameEncoder())
                        addLast(lengthFieldPrepender)
                        addLast(AgentMessageEncoder(
                                proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                    }
                }
            })
        }
        return proxyBootstrap
    }

    override fun channelInactive(agentChannelContext: ChannelHandlerContext) {
        ChannelInfoCache.removeChannelInfo(
                agentChannelContext.channel().id().asLongText())
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable) {
        ChannelInfoCache.removeChannelInfo(
                agentChannelContext.channel().id().asLongText())
        logger.error("Exception happen when setup the proxy connection.", cause)
        agentChannelContext.fireExceptionCaught(cause)
    }
}
