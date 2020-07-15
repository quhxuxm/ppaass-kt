package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelCacheInfo
import com.ppaass.kt.agent.handler.http.uitl.HttpConnectionInfoUtil
import com.ppaass.kt.agent.handler.http.uitl.HttpProxyUtil
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.common.message.MessageBodyEncryptionType
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
        ChannelInboundHandlerAdapter() {
    companion object {
        private val channelCacheInfoMap = HashMap<String, ChannelCacheInfo>()
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
        if (msg !is FullHttpRequest) {
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
            this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
                    .addListener(ChannelConnectResultListener(agentChannelContext, httpConnectionInfo))
            agentChannelContext.fireChannelRead(msg)
            return
        }
        // A http request
        logger.debug("Incoming request is http protocol,  clientChannelId={}", clientChannelId)
        val httpDataRequestPromise = DefaultPromise<Channel>(this.businessEventExecutorGroup.next())
        msg.retain()
        httpDataRequestPromise.addListener(
                HttpDataRequestPromiseListener(msg, agentChannelContext, clientChannelId,
                        channelCacheInfoMap, agentConfiguration))
        val httpConnectionInfo = HttpConnectionInfoUtil.parseHttpConnectionInfo(msg.uri())
        this.proxyBootstrap.handler(
                HttpDataTransferChannelInitializer(agentChannelContext.channel(), this.businessEventExecutorGroup,
                        httpConnectionInfo, clientChannelId, httpDataRequestPromise,
                        this.agentConfiguration,
                        channelCacheInfoMap))
        this.proxyBootstrap.connect(this.agentConfiguration.proxyAddress, this.agentConfiguration.proxyPort).sync()
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