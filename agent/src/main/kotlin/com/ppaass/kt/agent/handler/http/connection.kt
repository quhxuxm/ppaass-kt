package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.AgentConfiguration
import com.ppaass.kt.agent.handler.DiscardHeartbeatHandler
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
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.EventExecutorGroup
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder

private data class ChannelCacheInfo(
        val channel: Channel,
        val targetHost: String,
        val targetPort: Int
)

private object HttpProxyUtil {
    private val logger = LoggerFactory.getLogger(HttpProxyUtil::class.java)
    private fun convertHttpRequest(httpRequest: HttpRequest): ByteArray? {
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
        return proxyChannel.writeAndFlush(agentMessage)
    }
}

private data class HttpConnectionInfo(val host: String, val port: Int)

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
            throw PpaassException("Fail to parse host name from uri.")
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
    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val channelCacheInfo = ChannelCacheInfo(proxyChannelContext.channel(), this.targetHost, this.port)
        this.channelCacheInfoMap.put(clientChannelId, channelCacheInfo)
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.CONNECT, agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost,
                channelCacheInfo.targetPort, null,
                clientChannelId, MessageBodyEncryptionType.random())
                .addListener(ChannelFutureListener { connectCommandFuture: ChannelFuture ->
                    if (!connectCommandFuture.isSuccess) {
                        proxyChannelConnectedPromise.setFailure(connectCommandFuture.cause())
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy because of exception.",
                                connectCommandFuture.cause())
                    }

                    proxyChannelConnectedPromise.setSuccess(connectCommandFuture.channel())
                })
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        this.agentChannel.writeAndFlush(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
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

@ChannelHandler.Sharable
class HttpConnectionHandler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {
    companion object {
        private val channelCacheInfoMap = HashMap<String, ChannelCacheInfo>()
        private val logger = LoggerFactory.getLogger(HttpConnectionHandler::class.java)
    }

    private val businessEventExecutorGroup: EventExecutorGroup
    private val httpProxyBootstrap: Bootstrap

    init {
        this.businessEventExecutorGroup =
                DefaultEventLoopGroup(this.agentConfiguration.staticAgentConfiguration.businessEventThreadNumber)
        this.httpProxyBootstrap = Bootstrap()
        with(this.httpProxyBootstrap) {
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

    override fun channelRead(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        if (msg !is HttpRequest) {
            //A https connection
            val channelCacheInfo = channelCacheInfoMap.get(clientChannelId)
            if (channelCacheInfo == null) {
                logger.error("Fail to find https channel cache information with client channel id ${clientChannelId}")
                throw PpaassException()
            }
            HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                    msg, clientChannelId, MessageBodyEncryptionType.random())
            agentChannelContext.fireChannelRead(msg)
            return
        }
        val proxyChannelConnectedPromise = DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
        if (HttpMethod.CONNECT === msg.method()) {
            //A https connect method
            proxyChannelConnectedPromise.addListener {
                if (!it.isSuccess) {
                    return@addListener
                }
                val proxyChannel = it.now as Channel
                with(agentChannelContext.pipeline()) {
                    addLast(ResourceClearHandler(proxyChannel))
                }
                val connectSuccessResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                agentChannelContext.writeAndFlush(connectSuccessResponse).addListener {
                    with(agentChannelContext.pipeline()) {
                        remove(HttpServerCodec::class.java.name)
                        remove(HttpObjectAggregator::class.java.name)
                        remove(ChunkedWriteHandler::class.java.name)
                    }
                }
            }
            val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
            this.httpProxyBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpsProxyChannel: SocketChannel) {
                    with(httpsProxyChannel.pipeline()) {
                        addLast(ChunkedWriteHandler())
                        addLast(Lz4FrameDecoder())
                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                                0, 4, 0,
                                4))
                        addLast(ProxyMessageDecoder())
                        addLast(DiscardHeartbeatHandler(agentChannelContext.channel()))
                        addLast(businessEventExecutorGroup,
                                TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                        httpConnectionInfo.host, httpConnectionInfo.port, channelCacheInfoMap,
                                        clientChannelId,
                                        agentConfiguration, proxyChannelConnectedPromise))
                        addLast(ResourceClearHandler(agentChannelContext.channel()))
                        addLast(Lz4FrameEncoder())
                        addLast(LengthFieldPrepender(4))
                        addLast(AgentMessageEncoder())
                    }
                }
            })
            this.httpProxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
                    .addListener {
                        if (!it.isSuccess) {
                            agentChannelContext.close()
                            throw PpaassException()
                        }
                    }
            return
        }
        // A http request
        proxyChannelConnectedPromise.addListener {
            if (!it.isSuccess) {
                return@addListener
            }
            val proxyChannel = it.now as Channel
            with(agentChannelContext.pipeline()) {
                addLast(ResourceClearHandler(proxyChannel))
            }
            val channelCacheInfo = channelCacheInfoMap.get(clientChannelId)
            if (channelCacheInfo == null) {
                logger.error("Fail to find https channel cache information with client channel id ${clientChannelId}")
                throw PpaassException()
            }
            HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                    msg, clientChannelId, MessageBodyEncryptionType.random())
        }
        val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
        this.httpProxyBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(httpProxyChannel: SocketChannel) {
                with(httpProxyChannel.pipeline()) {
                    addLast(ChunkedWriteHandler())
                    addLast(Lz4FrameDecoder())
                    addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                    addLast(ProxyMessageDecoder())
                    addLast(DiscardHeartbeatHandler(agentChannelContext.channel()))
                    addLast(ProxyMessageOriginalDataDecoder())
                    addLast(HttpResponseDecoder())
                    addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                    addLast(businessEventExecutorGroup,
                            TransferDataFromProxyToAgentHandler(agentChannelContext.channel(),
                                    httpConnectionInfo.host, httpConnectionInfo.port,
                                    channelCacheInfoMap, clientChannelId,
                                    agentConfiguration, proxyChannelConnectedPromise))
                    addLast(ResourceClearHandler(agentChannelContext.channel()))
                    addLast(Lz4FrameEncoder())
                    addLast(LengthFieldPrepender(4))
                    addLast(AgentMessageEncoder())
                }
            }
        })
        this.httpProxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort)
                .addListener {
                    if (!it.isSuccess) {
                        agentChannelContext.close()
                        throw PpaassException()
                    }
                }
    }
}