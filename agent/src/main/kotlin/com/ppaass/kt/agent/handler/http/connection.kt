package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.AgentConfiguration
import com.ppaass.kt.agent.handler.DiscardProxyHeartbeatHandler
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.*
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.*
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder

private data class ChannelCacheInfo(
        val channel: Channel,
        val targetHost: String,
        val targetPort: Int
) {
    override fun toString(): String {
        return "ChannelCacheInfo(channel=$channel, targetHost='$targetHost', targetPort=$targetPort)"
    }
}

private data class HttpConnectionInfo(val host: String, val port: Int) {
    override fun toString(): String {
        return "HttpConnectionInfo(host='$host', port=$port)"
    }
}

private object HttpProxyUtil {
    private val logger = LoggerFactory.getLogger(HttpProxyUtil::class.java)

    private fun convertHttpRequest(httpRequest: HttpRequest): ByteArray {
        val ch = EmbeddedChannel(HttpRequestEncoder())
        ch.writeOutbound(httpRequest)
        val httpRequestByteBuf = ch.readOutbound<ByteBuf>()
        val data = ByteBufUtil.getBytes(httpRequestByteBuf)
        ReferenceCountUtil.release(httpRequestByteBuf)
        return data
    }

    fun writeToProxy(bodyType: AgentMessageBodyType, secureToken: String, proxyChannel: Channel,
                     host: String, port: Int,
                     input: Any?,
                     clientChannelId: String, messageBodyEncryptionType: MessageBodyEncryptionType): ChannelFuture {
        var data: ByteArray? = null
        if (input != null) {
            data = if (input is HttpRequest) {
                convertHttpRequest(input)
            } else {
                ByteBufUtil.getBytes(input as ByteBuf)
            }
        }
        val agentMessage =
                AgentMessage(secureToken, messageBodyEncryptionType, agentMessageBody(bodyType, clientChannelId) {
                    originalData = data
                    targetAddress = host
                    targetPort = port
                })
        if (data != null) {
            logger.debug("The agent message write from agent to proxy is:\n{}\n", agentMessage)
        }
        return proxyChannel.writeAndFlush(agentMessage)
    }
}

private object HttpConnectionInfoUtil {
    private val logger = LoggerFactory.getLogger(HttpConnectionInfoUtil::class.java)
    private const val HTTP_SCHEMA = "http://"
    private const val HTTPS_SCHEMA = "https://"
    private const val SCHEMA_AND_HOST_SEP = "://"
    private const val HOST_NAME_AND_PORT_SEP = ":"
    private const val SLASH = "/"
    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_HTTPS_PORT = 443

    fun parseHttpConnectionInfo(uri: String): HttpConnectionInfo {
        if (uri.startsWith(HTTP_SCHEMA)) {
            val uriComponentsBuilder: UriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
            val uriComponents: UriComponents = uriComponentsBuilder.build()
            var port: Int = uriComponents.getPort()
            if (port < 0) {
                port = DEFAULT_HTTP_PORT
            }
            return HttpConnectionInfo(uriComponents.host ?: "", port)
        }
        if (uri.startsWith(HTTPS_SCHEMA)) {
            val uriComponentsBuilder: UriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
            val uriComponents: UriComponents = uriComponentsBuilder.build()
            var port: Int = uriComponents.getPort()
            if (port < 0) {
                port = DEFAULT_HTTPS_PORT
            }
            return HttpConnectionInfo(uriComponents.host ?: "", port)
        }
        //For CONNECT method, only HTTPS will do this method.
        val schemaAndHostNameSepIndex = uri.indexOf(SCHEMA_AND_HOST_SEP)
        var hostNameAndPort = uri
        if (schemaAndHostNameSepIndex >= 0) {
            hostNameAndPort = uri.substring(
                    schemaAndHostNameSepIndex + SCHEMA_AND_HOST_SEP.length)
        }
        if (hostNameAndPort.contains(SLASH)) {
            logger.error("Can not parse host name from uri: {}", uri)
            throw PpaassException("Can not parse host name from uri: $uri")
        }
        val hostNameAndPortParts =
                hostNameAndPort.split(HOST_NAME_AND_PORT_SEP).toTypedArray()
        val hostName = hostNameAndPortParts[0]
        var port = DEFAULT_HTTPS_PORT
        if (hostNameAndPortParts.size > 1) {
            port = hostNameAndPortParts[1].toInt()
        }
        return HttpConnectionInfo(hostName, port)
    }
}

private class TransferDataFromProxyToAgentHandler(private val agentChannel: Channel, private val targetHost: String,
                                                  private val port: Int,
                                                  private val channelCacheInfoMap: MutableMap<String, ChannelCacheInfo>,
                                                  private val clientChannelId: String,
                                                  private val agentConfiguration: AgentConfiguration,
                                                  private val proxyChannelConnectedPromise: Promise<Channel>) :
        ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromProxyToAgentHandler::class.java)
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val channelCacheInfo = ChannelCacheInfo(proxyChannelContext.channel(), this.targetHost, this.port)
        this.channelCacheInfoMap[clientChannelId] = channelCacheInfo
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.CONNECT, agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost,
                channelCacheInfo.targetPort, null,
                clientChannelId, MessageBodyEncryptionType.random())
                .addListener(ChannelFutureListener { connectCommandFuture: ChannelFuture ->
                    if (!connectCommandFuture.isSuccess) {
                        proxyChannelConnectedPromise.setFailure(connectCommandFuture.cause())
                        logger.error(
                                "Fail to send connect message from agent to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                                clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, targetHost=${channelCacheInfo.targetHost}, targetPort =${channelCacheInfo.targetPort}",
                                connectCommandFuture.cause())
                    }
                    logger.debug("Success connect to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                            clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                    proxyChannelConnectedPromise.setSuccess(connectCommandFuture.channel())
                })
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        logger.debug(
                "Current client channel to receive the proxy response (reading), clientChannelId={}",
                this.clientChannelId)
        this.agentChannel.writeAndFlush(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        logger.debug(
                "Current client channel to receive the proxy response (read complete), clientChannelId={}",
                this.clientChannelId)
        this.agentChannel.flush()
    }
}

@ChannelHandler.Sharable
private class ProxyMessageOriginalDataDecoder : MessageToMessageDecoder<ProxyMessage>() {
    override fun decode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: MutableList<Any>) {
        val proxyDataBuf = ctx.alloc().buffer()
        proxyDataBuf.writeBytes(msg.body.originalData)
        out.add(proxyDataBuf)
    }
}

private class HttpDataTransferChannelInitializer(private val agentChannel: Channel,
                                                 private val executorGroup: EventExecutorGroup,
                                                 private val httpConnectionInfo: HttpConnectionInfo,
                                                 private val clientChannelId: String,
                                                 private val proxyChannelConnectedPromise: Promise<Channel>,
                                                 private val agentConfiguration: AgentConfiguration,
                                                 private val channelCacheInfoMap: HashMap<String, ChannelCacheInfo>) :
        ChannelInitializer<SocketChannel>() {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpDataTransferChannelInitializer::class.java)
    }

    override fun initChannel(httpProxyChannel: SocketChannel) {
        logger.debug("Initialize HTTP data transfer channel, clientChannelId={}, channelCacheInfoMap={}",
                clientChannelId, channelCacheInfoMap)
        with(httpProxyChannel.pipeline()) {
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                    0, 4, 0,
                    4))
            addLast(ProxyMessageDecoder())
            addLast(DiscardProxyHeartbeatHandler(agentChannel))
            addLast(ProxyMessageOriginalDataDecoder())
            addLast(HttpResponseDecoder())
            addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
            addLast(executorGroup,
                    TransferDataFromProxyToAgentHandler(agentChannel,
                            httpConnectionInfo.host, httpConnectionInfo.port,
                            channelCacheInfoMap, clientChannelId,
                            agentConfiguration, proxyChannelConnectedPromise))
            addLast(ResourceClearHandler(agentChannel))
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(AgentMessageEncoder())
        }
    }
}

private class HttpsDataTransferChannelInitializer(private val agentChannel: Channel,
                                                  private val executorGroup: EventExecutorGroup,
                                                  private val httpConnectionInfo: HttpConnectionInfo,
                                                  private val clientChannelId: String,
                                                  private val proxyChannelConnectedPromise: Promise<Channel>,
                                                  private val agentConfiguration: AgentConfiguration,
                                                  private val channelCacheInfoMap: HashMap<String, ChannelCacheInfo>) :
        ChannelInitializer<SocketChannel>() {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpsDataTransferChannelInitializer::class.java)
    }

    override fun initChannel(httpsProxyChannel: SocketChannel) {
        logger.debug("Initialize HTTPS data transfer channel, clientChannelId={}, channelCacheInfoMap={}",
                clientChannelId, channelCacheInfoMap)
        with(httpsProxyChannel.pipeline()) {
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                    0, 4, 0,
                    4))
            addLast(ProxyMessageDecoder())
            addLast(DiscardProxyHeartbeatHandler(agentChannel))
            addLast(executorGroup,
                    TransferDataFromProxyToAgentHandler(agentChannel,
                            httpConnectionInfo.host, httpConnectionInfo.port,
                            channelCacheInfoMap,
                            clientChannelId,
                            agentConfiguration, proxyChannelConnectedPromise))
            addLast(ResourceClearHandler(agentChannel))
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(AgentMessageEncoder())
        }
    }
}

private class HttpsConnectRequestPromiseListener(
        private val agentChannelContext: ChannelHandlerContext) :
        GenericFutureListener<Future<Channel>> {
    companion object {
        private val logger =
                LoggerFactory.getLogger(HttpsConnectRequestPromiseListener::class.java)
    }

    override fun operationComplete(promiseFuture: Future<Channel>) {
        if (!promiseFuture.isSuccess) {
            return
        }
        val promiseChannel = promiseFuture.now as Channel
        with(this.agentChannelContext.pipeline()) {
            addLast(ResourceClearHandler(promiseChannel))
        }
        val okResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        agentChannelContext.writeAndFlush(okResponse)
                .addListener(ChannelFutureListener { okResponseFuture ->
                    if (!okResponseFuture.isSuccess) {
                        logger.error(
                                "Fail to send ok response to agent client because of exception.",
                                okResponseFuture.cause())
                        throw PpaassException("Fail to send ok response to agent client because of exception",
                                okResponseFuture.cause())
                    }
                    with(okResponseFuture.channel().pipeline()) {
                        remove(HttpServerCodec::class.java.name)
                        remove(HttpObjectAggregator::class.java.name)
                        remove(ChunkedWriteHandler::class.java.name)
                    }
                })
    }
}

private class HttpDataRequestPromiseListener(private val message: Any,
                                             private val agentChannelContext: ChannelHandlerContext,
                                             private val clientChannelId: String,
                                             private val channelCacheInfoMap: HashMap<String, ChannelCacheInfo>,
                                             private val agentConfiguration: AgentConfiguration) :
        GenericFutureListener<Future<Channel>> {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpDataRequestPromiseListener::class.java)
    }

    override fun operationComplete(future: Future<Channel>) {
        if (!future.isSuccess) {
            return
        }

        with(agentChannelContext.pipeline()) {
            addLast(ResourceClearHandler(future.now))
        }
        val channelCacheInfo = channelCacheInfoMap[clientChannelId]
        if (channelCacheInfo == null) {
            logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
            throw PpaassException()
        }
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                message, clientChannelId, MessageBodyEncryptionType.random())
    }
}

private class ChannelConnectResultListener(private val agentChannelContext: ChannelHandlerContext,
                                           private val httpConnectionInfo: HttpConnectionInfo) : ChannelFutureListener {
    companion object {
        private val logger = LoggerFactory.getLogger(ChannelConnectResultListener::class.java)
    }

    override fun operationComplete(future: ChannelFuture) {
        if (!future.isSuccess) {
            agentChannelContext.close()
            logger.error("Fail to connect to proxy server because of exception.",
                    future.cause())
            throw PpaassException("Fail to connect to proxy server because of exception.", future.cause())
        }
        logger.debug(
                "Success connect to proxy server for target server, targetAddress={}, targetPort={}",
                httpConnectionInfo.host, httpConnectionInfo.port)
    }
}

@ChannelHandler.Sharable
class HttpOrHttpsConnectionHandler(private val agentConfiguration: AgentConfiguration) :
        ChannelInboundHandlerAdapter() {
    companion object {
        private val channelCacheInfoMap = HashMap<String, ChannelCacheInfo>()
        private val logger = LoggerFactory.getLogger(HttpOrHttpsConnectionHandler::class.java)
    }

    private val businessEventExecutorGroup: EventExecutorGroup
    private val proxyBootstrap: Bootstrap

    init {
        this.businessEventExecutorGroup =
                DefaultEventLoopGroup(this.agentConfiguration.staticAgentConfiguration.businessEventThreadNumber)
        this.proxyBootstrap = Bootstrap()
        with(this.proxyBootstrap) {
            group(NioEventLoopGroup(
                    agentConfiguration.staticAgentConfiguration.proxyDataTransferIoEventThreadNumber))
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.RCVBUF_ALLOCATOR,
                    AdaptiveRecvByteBufAllocator(
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferMinSize,
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferInitialSize,
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferMaxSize))
            option(ChannelOption.SO_RCVBUF,
                    agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
        }
    }

    private fun sendRequestToProxy(clientChannelId: String, msg: Any) {
        val channelCacheInfo = channelCacheInfoMap[clientChannelId]
        if (channelCacheInfo == null) {
            logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
            throw PpaassException()
        }
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                msg, clientChannelId, MessageBodyEncryptionType.random())
    }

    override fun channelRead(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        logger.debug("Agent receive a client connection, clientChannelId={}", clientChannelId)
        if (msg !is HttpRequest) {
            //A https request to send data
            logger.debug("Incoming request is https protocol to send data, clientChannelId={}", clientChannelId)
            this.sendRequestToProxy(clientChannelId, msg)
            agentChannelContext.fireChannelRead(msg)
            return
        }
        if (HttpMethod.CONNECT === msg.method()) {
            //A https request to setup the connection
            logger.debug("Incoming request is https protocol to setup connection, clientChannelId={}", clientChannelId)
            val httpsConnectRequestPromise =
                    DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
            httpsConnectRequestPromise.addListener(
                    HttpsConnectRequestPromiseListener(agentChannelContext))
            val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
            this.proxyBootstrap.handler(
                    HttpsDataTransferChannelInitializer(agentChannelContext.channel(), this.businessEventExecutorGroup,
                            httpConnectionInfo, clientChannelId, httpsConnectRequestPromise,
                            this.agentConfiguration,
                            channelCacheInfoMap))
            this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
                    .addListener(ChannelConnectResultListener(agentChannelContext, httpConnectionInfo))
            agentChannelContext.fireChannelRead(msg)
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        val httpDataRequestPromise = DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
        httpDataRequestPromise.addListener(
                HttpDataRequestPromiseListener(msg, agentChannelContext, clientChannelId,
                        channelCacheInfoMap, agentConfiguration))
        val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
        this.proxyBootstrap.handler(
                HttpDataTransferChannelInitializer(agentChannelContext.channel(), this.businessEventExecutorGroup,
                        httpConnectionInfo, clientChannelId, httpDataRequestPromise,
                        this.agentConfiguration,
                        channelCacheInfoMap))
        this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
                .addListener(ChannelConnectResultListener(agentChannelContext, httpConnectionInfo))
        agentChannelContext.fireChannelRead(msg)
    }

    override fun channelInactive(agentChannelContext: ChannelHandlerContext) {
        channelCacheInfoMap.remove(
                agentChannelContext.channel().id().asLongText())
    }

    override fun channelReadComplete(agentChannelContext: ChannelHandlerContext) {
        agentChannelContext.flush()
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable) {
        channelCacheInfoMap.remove(
                agentChannelContext.channel().id().asLongText())
        agentChannelContext.fireExceptionCaught(cause)
    }
}