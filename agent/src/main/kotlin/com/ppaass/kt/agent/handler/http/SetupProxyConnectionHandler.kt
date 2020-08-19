package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.discardProxyHeartbeatHandler
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
import io.netty.util.concurrent.DefaultPromise
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SetupProxyConnectionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<Any>() {
    private companion object {
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
            writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    channelCacheInfo.proxyChannel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                    msg, clientChannelId, MessageBodyEncryptionType.random())
            return
        }
        if (HttpMethod.CONNECT === msg.method()) {
            //A https request to setup the connection
            logger.debug("Incoming request is https protocol to setup connection, clientChannelId={}", clientChannelId)

            val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
            this.proxyBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
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
                        addLast(
                                TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                        httpConnectionInfo.host, httpConnectionInfo.port,
                                        clientChannelId,
                                        agentConfiguration))
                        addLast(resourceClearHandler)
                        addLast(Lz4FrameEncoder())
                        addLast(lengthFieldPrepender)
                        addLast(AgentMessageEncoder(
                                proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                    }
                }
            })
            this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
                    .addListener {
                        if (!it.isSuccess) {
                            agentChannelContext.close()
                            logger.error("Fail to connect to proxy server because of exception.",
                                    it.cause())
                            throw PpaassException("Fail to connect to proxy server because of exception.", it.cause())
                        }
                        with(agentChannelContext.pipeline()) {
                            addLast(resourceClearHandler)
                        }
                        val okResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                        agentChannelContext.writeAndFlush(okResponse)
                                .addListener(ChannelFutureListener { okResponseFuture ->
                                    okResponseFuture.channel().pipeline().apply {
                                        if (this[HttpServerCodec::class.java.name] != null) {
                                            remove(HttpServerCodec::class.java.name)
                                        }
                                        if (this[HttpObjectAggregator::class.java.name] != null) {
                                            remove(HttpObjectAggregator::class.java.name)
                                        }
                                    }
                                    logger.debug(
                                            "Success connect to proxy server for target server, targetAddress={}, targetPort={}",
                                            httpConnectionInfo.host, httpConnectionInfo.port)
                                })

                    }
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        val proxyChannelActivePromise = DefaultPromise<Channel>(agentChannelContext.executor())
        ReferenceCountUtil.retain(msg, 1)

        val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
        this.proxyBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(httpProxyChannel: SocketChannel) {
                httpProxyChannel.pipeline().apply {
                    addLast(Lz4FrameDecoder())
                    addLast(LengthFieldBasedFrameDecoder(java.lang.Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                    addLast(ProxyMessageDecoder(
                            agentPrivateKeyString = agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                    addLast(discardProxyHeartbeatHandler)
                    addLast(ExtractProxyMessageOriginalDataDecoder())
                    addLast(HttpResponseDecoder())
                    addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                    addLast(
                            TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                    httpConnectionInfo.host, httpConnectionInfo.port,
                                    clientChannelId,
                                    agentConfiguration))
                    addLast(resourceClearHandler)
                    addLast(Lz4FrameEncoder())
                    addLast(lengthFieldPrepender)
                    addLast(AgentMessageEncoder(
                            proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                }
            }
        })
        this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
                .addListener {
                    if (!it.isSuccess) {
                        agentChannelContext.close()
                        logger.error("Fail to connect to proxy server because of exception.",
                                it.cause())
                        throw PpaassException("Fail to connect to proxy server because of exception.", it.cause())
                    }
                    with(agentChannelContext.pipeline()) {
                        addLast(resourceClearHandler)
                    }
                    val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
                    if (channelCacheInfo == null) {
                        logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
                        throw PpaassException()
                    }
                    writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                            channelCacheInfo.proxyChannel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                            msg, clientChannelId, MessageBodyEncryptionType.random())
                    logger.debug(
                            "Success connect to proxy server for target server, targetAddress={}, targetPort={}",
                            httpConnectionInfo.host, httpConnectionInfo.port)
                }
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
