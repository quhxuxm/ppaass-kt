package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.PreForwardProxyMessageHandler
import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseDecoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class HttpProxySetupConnectionHandler(
    private val agentConfiguration: AgentConfiguration,
    private val preForwardProxyMessageHandler: PreForwardProxyMessageHandler,
    private val transferDataFromProxyToAgentHandler: TransferDataFromProxyToAgentHandler,
    private val resourceClearHandler: ResourceClearHandler,
    private val proxyBootstrapIoEventLoopGroup: EventLoopGroup,
    private val dataTransferIoEventLoopGroup: EventLoopGroup) :
    SimpleChannelInboundHandler<Any>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        logger.debug("Agent receive a client connection, clientChannelId={}", clientChannelId)
        if (msg !is FullHttpRequest) {
            //A https request to send data
            logger.debug("Incoming request is https protocol to send data, clientChannelId={}", clientChannelId)
            val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
            if (channelCacheInfo == null) {
                agentChannelContext.close()
                logger.debug("Fail to find channel cache information, clientChannelId={}", clientChannelId)
                return
            }
            if (!channelCacheInfo.proxyChannel.isActive) {
                channelCacheInfo.proxyChannel.close()
                channelCacheInfo.agentChannel.close()
                logger.debug("Fail to write data to proxy channel because of it is inactive, clientChannelId={}",
                    clientChannelId)
                return
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
            val proxyBootstrapForHttps =
                createProxyBootstrapForHttps(agentChannelContext, httpConnectionInfo) {
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
            proxyBootstrapForHttps.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
                .addListener {
                    if (!it.isSuccess) {
                        agentChannelContext.close()
                        logger.debug("Fail to connect to proxy server because of exception.",
                            it.cause())
                        return@addListener
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
                        logger.debug(
                            "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, " +
                                "targetHost=${channelCacheInfo.targetHost}, targetPort =${channelCacheInfo.targetPort}",
                            it.cause())
                        return@writeAgentMessageToProxy
                    }
                }
                return
            }
        }
        val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
        val proxyBootstrap =
            createProxyBootstrapForHttp(agentChannelContext, httpConnectionInfo) { proxyChannelContext ->
                writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    proxyChannelContext.channel(), httpConnectionInfo.host, httpConnectionInfo.port,
                    msg, clientChannelId, MessageBodyEncryptionType.random()) {
                    if (!it.isSuccess) {
                        ChannelInfoCache.removeChannelInfo(clientChannelId)
                        agentChannelContext.close()
                        proxyChannelContext.close()
                        logger.debug(
                            "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, " +
                                "targetHost=${httpConnectionInfo.host}, targetPort =${httpConnectionInfo.port}",
                            it.cause())
                        return@writeAgentMessageToProxy
                    }
                }
            }
        proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
            .addListener(ChannelFutureListener {
                if (!it.isSuccess) {
                    ChannelInfoCache.removeChannelInfo(clientChannelId)
                    agentChannelContext.close()
                    it.channel().close()
                    logger.error("Fail to connect to proxy server because of exception.",
                        it.cause())
                    return@ChannelFutureListener
                }
            })
    }

    private fun createProxyBootstrapForHttp(agentChannelContext: ChannelHandlerContext,
                                            httpConnectionInfo: HttpConnectionInfo,
                                            initOnChannelActivate: (proxyChannelContext: ChannelHandlerContext) -> Unit = {}): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyBootstrapIoEventLoopGroup)
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
            attr(AGENT_CHANNEL_CONTEXT, agentChannelContext)
            attr(HTTP_CONNECTION_INFO, httpConnectionInfo)
            attr(PROXY_CHANNEL_ACTIVE_CALLBACK, initOnChannelActivate)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpProxyChannel: SocketChannel) {
                    httpProxyChannel.pipeline().apply {
//                    addLast(Lz4FrameDecoder())
                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                        addLast(ProxyMessageDecoder(agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                        addLast(preForwardProxyMessageHandler)
                        addLast(ExtractProxyMessageOriginalDataDecoder())
                        addLast(HttpResponseDecoder())
                        addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                        addLast(dataTransferIoEventLoopGroup, transferDataFromProxyToAgentHandler)
                        addLast(resourceClearHandler)
//                    addLast(Lz4FrameEncoder())
                        addLast(LengthFieldPrepender(4))
                        addLast(AgentMessageEncoder(agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                    }
                }
            })
        }
        return proxyBootstrap
    }

    private fun createProxyBootstrapForHttps(agentChannelContext: ChannelHandlerContext,
                                             httpConnectionInfo: HttpConnectionInfo,
                                             initOnChannelActivate: (proxyChannelContext: ChannelHandlerContext) -> Unit = {}): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyBootstrapIoEventLoopGroup)
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
            attr(AGENT_CHANNEL_CONTEXT, agentChannelContext)
            attr(HTTP_CONNECTION_INFO, httpConnectionInfo)
            attr(PROXY_CHANNEL_ACTIVE_CALLBACK, initOnChannelActivate)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpsProxyChannel: SocketChannel) {
                    with(httpsProxyChannel.pipeline()) {
//                        addLast(Lz4FrameDecoder())
                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                        addLast(ProxyMessageDecoder(agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                        addLast(preForwardProxyMessageHandler)
                        addLast(ExtractProxyMessageOriginalDataDecoder())
                        addLast(dataTransferIoEventLoopGroup, transferDataFromProxyToAgentHandler)
                        addLast(resourceClearHandler)
//                        addLast(Lz4FrameEncoder())
                        addLast(LengthFieldPrepender(4))
                        addLast(AgentMessageEncoder(agentConfiguration.staticAgentConfiguration.proxyPublicKey))
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
        val channelCacheInfo =
            ChannelInfoCache.getChannelInfoByClientChannelId(agentChannelContext.channel().id().asLongText())
        if (channelCacheInfo != null) {
            channelCacheInfo.agentChannel.close()
            channelCacheInfo.proxyChannel.close()
            ChannelInfoCache.removeChannelInfo(
                agentChannelContext.channel().id().asLongText())
        }
        logger.error("Exception happen when setup the proxy connection.", cause)
    }
}
