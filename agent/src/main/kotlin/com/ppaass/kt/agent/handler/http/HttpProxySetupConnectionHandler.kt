package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
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
    private val proxyBootstrapForHttp: Bootstrap,
    private val proxyBootstrapForHttps: Bootstrap,
) :
    SimpleChannelInboundHandler<Any>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        logger.debug("Agent receive a client connection, clientChannelId={}", clientChannelId)
        if (msg !is FullHttpRequest) {
            //A https request to send data
            logger.debug("Incoming request is https protocol to send data, clientChannelId={}",
                clientChannelId)
            val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
            if (channelCacheInfo == null) {
                agentChannelContext.close()
                logger.debug("Fail to find channel cache information, clientChannelId={}",
                    clientChannelId)
                return
            }
            if (!channelCacheInfo.proxyChannel.isActive) {
                channelCacheInfo.proxyChannel.close()
                channelCacheInfo.agentChannel.close()
                logger.debug(
                    "Fail to write data to proxy channel because of it is inactive, clientChannelId={}",
                    clientChannelId)
                return
            }
            writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                channelCacheInfo.proxyChannel, channelCacheInfo.targetHost,
                channelCacheInfo.targetPort,
                msg, clientChannelId, MessageBodyEncryptionType.random())
            return
        }
        if (HttpMethod.CONNECT === msg.method()) {
            //A https request to setup the connection
            logger.debug(
                "Incoming request is https protocol to setup connection, clientChannelId={}",
                clientChannelId)
            val connectionHeader =
                msg.headers()[HttpHeaderNames.PROXY_CONNECTION]
                    ?: msg.headers()[HttpHeaderNames.CONNECTION]
            val connectionKeepAlive =
                connectionHeader?.toLowerCase()?.equals(HttpHeaderValues.KEEP_ALIVE) ?: false
            val httpConnectionInfo = parseHttpConnectionInfo(msg.uri())
            this.proxyBootstrapForHttps.connect(this.agentConfiguration.proxyAddress,
                this.agentConfiguration.proxyPort)
                .addListener(ChannelFutureListener { proxyChannelFuture ->
                    if (!proxyChannelFuture.isSuccess) {
                        agentChannelContext.close()
                        logger.debug("Fail to connect to proxy server because of exception.",
                            proxyChannelFuture.cause())
                        return@ChannelFutureListener
                    }
                    proxyChannelFuture.channel().attr(AGENT_CHANNEL_CONTEXT)
                        .setIfAbsent(agentChannelContext)
                    proxyChannelFuture.channel().attr(HTTP_CONNECTION_INFO)
                        .setIfAbsent(httpConnectionInfo)
                    proxyChannelFuture.channel().attr(HTTP_CONNECTION_KEEP_ALIVE)
                        .setIfAbsent(connectionKeepAlive)
                    proxyChannelFuture.channel().attr(HTTP_CONNECTION_IS_HTTPS).setIfAbsent(true)
                })
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        ReferenceCountUtil.retain(msg, 1)
        val channelCacheInfo = ChannelInfoCache.getChannelInfoByClientChannelId(clientChannelId)
        if (channelCacheInfo != null) {
            if (channelCacheInfo.proxyChannel.isActive) {
                writeAgentMessageToProxy(AgentMessageBodyType.DATA,
                    this.agentConfiguration.userToken,
                    channelCacheInfo.proxyChannel, channelCacheInfo.targetHost,
                    channelCacheInfo.targetPort,
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
        val connectionHeader =
            msg.headers()[HttpHeaderNames.PROXY_CONNECTION]
                ?: msg.headers()[HttpHeaderNames.CONNECTION]
        val connectionKeepAlive =
            connectionHeader?.toLowerCase()?.equals(HttpHeaderValues.KEEP_ALIVE) ?: false
        this.proxyBootstrapForHttp.connect(this.agentConfiguration.proxyAddress,
            this.agentConfiguration.proxyPort)
            .addListener(ChannelFutureListener { proxyChannelFuture ->
                if (!proxyChannelFuture.isSuccess) {
                    ChannelInfoCache.removeChannelInfo(clientChannelId)
                    agentChannelContext.close()
                    proxyChannelFuture.channel().close()
                    logger.error("Fail to connect to proxy server because of exception.",
                        proxyChannelFuture.cause())
                    return@ChannelFutureListener
                }
                proxyChannelFuture.channel().attr(AGENT_CHANNEL_CONTEXT)
                    .setIfAbsent(agentChannelContext)
                proxyChannelFuture.channel().attr(HTTP_CONNECTION_INFO)
                    .setIfAbsent(httpConnectionInfo)
                proxyChannelFuture.channel().attr(HTTP_CONNECTION_KEEP_ALIVE)
                    .setIfAbsent(connectionKeepAlive)
                proxyChannelFuture.channel().attr(HTTP_CONNECTION_IS_HTTPS).setIfAbsent(false)
                proxyChannelFuture.channel().attr(HTTP_MESSAGE).setIfAbsent(msg)



            })
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
            ChannelInfoCache.getChannelInfoByClientChannelId(
                agentChannelContext.channel().id().asLongText())
        if (channelCacheInfo != null) {
            channelCacheInfo.agentChannel.close()
            channelCacheInfo.proxyChannel.close()
            ChannelInfoCache.removeChannelInfo(
                agentChannelContext.channel().id().asLongText())
        }
        logger.error("Exception happen when setup the proxy connection.", cause)
    }
}
