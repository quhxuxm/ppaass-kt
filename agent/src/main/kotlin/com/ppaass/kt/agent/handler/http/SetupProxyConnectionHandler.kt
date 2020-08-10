package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.uitl.ChannelInfoCache
import com.ppaass.kt.agent.handler.http.uitl.HttpConnectionInfoUtil
import com.ppaass.kt.agent.handler.http.uitl.HttpProxyUtil
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.EventExecutorGroup
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class SetupProxyConnectionHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<Any>() {
    companion object {
        private val logger = LoggerFactory.getLogger(SetupProxyConnectionHandler::class.java)
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
        }
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val clientChannelId = agentChannelContext.channel().id().asLongText()
        logger.debug("Agent receive a client connection, clientChannelId={}", clientChannelId)
        if (msg !is FullHttpRequest) {
            //A https request to send data
            logger.debug("Incoming request is https protocol to send data, clientChannelId={}", clientChannelId)
            val channelCacheInfo = ChannelInfoCache.getChannelInfo(clientChannelId)
            if (channelCacheInfo == null) {
                logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
                throw PpaassException("Fail to find channel cache information, clientChannelId=$clientChannelId")
            }
            HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                    channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                    msg, clientChannelId, MessageBodyEncryptionType.random())
            return
        }
        if (HttpMethod.CONNECT === msg.method()) {
            //A https request to setup the connection
            logger.debug("Incoming request is https protocol to setup connection, clientChannelId={}", clientChannelId)
            val proxyChannelActivePromise =
                    DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
            proxyChannelActivePromise.addListener(
                    HttpsConnectRequestPromiseListener(agentChannelContext))
            val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
            this.proxyBootstrap.handler(
                    HttpsDataTransferChannelInitializer(agentChannelContext.channel(), this.businessEventExecutorGroup,
                            httpConnectionInfo, clientChannelId, proxyChannelActivePromise,
                            this.agentConfiguration))
            this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
                    .addListener(ProxyChannelConnectedListener(agentChannelContext, httpConnectionInfo))
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        val proxyChannelActivePromise = DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
        proxyChannelActivePromise.addListener(
                ProxyChannelActiveListener(msg, agentChannelContext, clientChannelId, agentConfiguration))
        val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
        this.proxyBootstrap.handler(
                HttpDataTransferChannelInitializer(agentChannelContext.channel(), this.businessEventExecutorGroup,
                        httpConnectionInfo, clientChannelId, proxyChannelActivePromise,
                        this.agentConfiguration))
        this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
                .addListener(ProxyChannelConnectedListener(agentChannelContext, httpConnectionInfo))
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