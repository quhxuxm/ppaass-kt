package com.ppaass.agent.handler.http

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessage
import com.ppaass.kt.common.AgentMessageBody
import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.AgentMessageEncoder
import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.PrintExceptionHandler
import com.ppaass.kt.common.ProxyMessageDecoder
import com.ppaass.kt.common.generateUuid
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestEncoder
import io.netty.handler.codec.http.HttpResponseDecoder
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriComponentsBuilder

private val logger = KotlinLogging.logger { }
private const val HTTP_SCHEMA = "http://"
private const val HTTPS_SCHEMA = "https://"
private const val SCHEMA_AND_HOST_SEP = "://"
private const val HOST_NAME_AND_PORT_SEP = ":"
private const val SLASH = "/"
private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

internal fun parseConnectionInfo(uri: String): HttpConnectionInfo? {
    if (uri.startsWith(HTTP_SCHEMA)) {
        val uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
        val uriComponents = uriComponentsBuilder.build()
        var port = uriComponents.port
        if (port < 0) {
            port = DEFAULT_HTTP_PORT
        }
        val targetHost = uriComponents.host ?: ""
        val connectionInfo = HttpConnectionInfo(
            targetHost = targetHost,
            targetPort = port,
            isHttps = false
        )
        return connectionInfo
    }
    if (uri.startsWith(HTTPS_SCHEMA)) {
        val uriComponentsBuilder = UriComponentsBuilder.fromUriString(uri)
        val uriComponents = uriComponentsBuilder.build()
        var port = uriComponents.port
        if (port < 0) {
            port = DEFAULT_HTTPS_PORT
        }
        val targetHost = uriComponents.host ?: ""
        val connectionInfo = HttpConnectionInfo(
            targetHost = targetHost,
            targetPort = port,
            isHttps = true
        )
        return connectionInfo
    }
    //For CONNECT method, only HTTPS will do this method.
    val schemaAndHostNameSepIndex = uri.indexOf(
        SCHEMA_AND_HOST_SEP)
    var hostNameAndPort = uri
    if (schemaAndHostNameSepIndex >= 0) {
        hostNameAndPort = uri.substring(
            schemaAndHostNameSepIndex + SCHEMA_AND_HOST_SEP.length)
    }
    if (hostNameAndPort.contains(
            SLASH)) {
        return null
    }
    val hostNameAndPortParts = hostNameAndPort.split(
        HOST_NAME_AND_PORT_SEP.toRegex()).toTypedArray()
    val hostName = hostNameAndPortParts[0]
    var port = DEFAULT_HTTPS_PORT
    if (hostNameAndPortParts.size > 1) {
        try {
            port = hostNameAndPortParts[1].toInt()
        } catch (e: NumberFormatException) {
            logger.warn("Fail to parse port from request uri, uri = {}", uri)
        }
    }
    val targetHost = hostName
    val connectionInfo = HttpConnectionInfo(
        targetHost = targetHost,
        targetPort = port,
        isHttps = true
    )
    return connectionInfo
}

internal fun writeAgentMessageToProxy(bodyType: AgentMessageBodyType, userToken: String,
                                      proxyChannel: Channel, input: Any?,
                                      targetHost: String, targetPort: Int,
                                      writeCallback: ChannelFutureListener?) {
    val data = input?.let {
        (input as? HttpRequest)?.let innerLet@{
            val tempChannel = EmbeddedChannel(HttpRequestEncoder())
            tempChannel.writeOutbound(input)
            val httpRequestByteBuf = tempChannel.readOutbound<ByteBuf>()
            return@let ByteBufUtil.getBytes(httpRequestByteBuf)
        }
        (input as? ByteBuf)?.let innerLet@{
            return@let ByteBufUtil.getBytes(input)
        }
        return@let byteArrayOf()
    } ?: byteArrayOf()
    val agentMessageBody =
        AgentMessageBody(
            id = generateUuid(),
            bodyType = bodyType,
            userToken = userToken,
            targetHost = targetHost,
            targetPort = targetPort,
            data = data)
    val agentMessage =
        AgentMessage(
            encryptionToken = generateUuid(),
            encryptionType = EncryptionType.choose(),
            body = agentMessageBody)
    val writeResultFuture = proxyChannel.writeAndFlush(agentMessage)
    writeCallback?.let {
        writeResultFuture.addListener(writeCallback)
    }
}

@Configuration
private class HttpConfigure {
    @Bean
    fun proxyBootstrapForHttp(proxyTcpLoopGroup: EventLoopGroup,
                              httpProxyToAgentHandler: HttpProxyToAgentHandler,
                              printExceptionHandler: PrintExceptionHandler,
                              agentConfiguration: AgentConfiguration,
                              httpProxyMessageBodyTypeHandler: HttpProxyMessageBodyTypeHandler) =
        Bootstrap().apply {
            group(proxyTcpLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.proxyTcpConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, false)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_LINGER,
                agentConfiguration.proxyTcpSoLinger)
            option(ChannelOption.SO_RCVBUF,
                agentConfiguration.proxyTcpSoRcvbuf)
            option(ChannelOption.SO_SNDBUF,
                agentConfiguration.proxyTcpSoSndbuf)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(proxyChannel: SocketChannel) {
                    val proxyChannelPipeline = proxyChannel.pipeline()
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        proxyChannelPipeline.addLast(Lz4FrameDecoder())
                    }
                    proxyChannelPipeline.addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                        0, 4, 0,
                        4))
                    proxyChannelPipeline.addLast(ProxyMessageDecoder(
                        agentConfiguration.agentPrivateKey))
                    proxyChannelPipeline.addLast(httpProxyMessageBodyTypeHandler)
                    proxyChannelPipeline.addLast(HttpProxyMessageConvertToOriginalDataDecoder())
                    proxyChannelPipeline.addLast(HttpResponseDecoder())
                    proxyChannelPipeline.addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                    proxyChannelPipeline.addLast(httpProxyToAgentHandler)
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        proxyChannelPipeline.addLast(Lz4FrameEncoder())
                    }
                    proxyChannelPipeline.addLast(LengthFieldPrepender(4))
                    proxyChannelPipeline.addLast(AgentMessageEncoder(
                        agentConfiguration.proxyPublicKey))
                    proxyChannelPipeline.addLast(printExceptionHandler)
                }
            })
        }

    @Bean
    fun proxyBootstrapForHttps(proxyIoEventLoopGroup: EventLoopGroup?,
                               httpProxyToAgentHandler: HttpProxyToAgentHandler,
                               printExceptionHandler: PrintExceptionHandler,
                               agentConfiguration: AgentConfiguration,
                               httpProxyMessageBodyTypeHandler: HttpProxyMessageBodyTypeHandler) =
        Bootstrap().apply {
            group(proxyIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.proxyTcpConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, false)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_LINGER,
                agentConfiguration.proxyTcpSoLinger)
            option(ChannelOption.SO_RCVBUF,
                agentConfiguration.proxyTcpSoRcvbuf)
            option(ChannelOption.SO_SNDBUF,
                agentConfiguration.proxyTcpSoSndbuf)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(proxyChannel: SocketChannel) {
                    val proxyChannelPipeline = proxyChannel.pipeline()
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        proxyChannelPipeline.addLast(Lz4FrameDecoder())
                    }
                    proxyChannelPipeline.addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                        0, 4, 0,
                        4))
                    proxyChannelPipeline.addLast(ProxyMessageDecoder(
                        agentConfiguration.agentPrivateKey))
                    proxyChannelPipeline.addLast(httpProxyMessageBodyTypeHandler)
                    proxyChannelPipeline.addLast(HttpProxyMessageConvertToOriginalDataDecoder())
                    proxyChannelPipeline.addLast(httpProxyToAgentHandler)
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        proxyChannelPipeline.addLast(Lz4FrameEncoder())
                    }
                    proxyChannelPipeline.addLast(LengthFieldPrepender(4))
                    proxyChannelPipeline.addLast(AgentMessageEncoder(
                        agentConfiguration.proxyPublicKey))
                    proxyChannelPipeline.addLast(printExceptionHandler)
                }
            })
        }
}
