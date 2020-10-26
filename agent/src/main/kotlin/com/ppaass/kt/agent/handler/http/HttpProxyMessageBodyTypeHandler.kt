package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.socks.v5.AGENT_CHANNEL_CONTEXT
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class HttpProxyMessageBodyTypeHandler(private val agentConfiguration: AgentConfiguration) :
    SimpleChannelInboundHandler<ProxyMessage>(false) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              proxyMessage: ProxyMessage) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        if (ProxyMessageBodyType.HEARTBEAT == proxyMessage.body.bodyType) {
            val originalData = proxyMessage.body.originalData
            if (originalData == null) {
                throw PpaassException()
            }
            val utcDataTimeString = String(originalData, Charsets.UTF_8)
            logger.debug("Receive heartbeat form proxy channel: {}, heartbeat time: {}",
                proxyChannelContext.channel().id().asLongText(),
                utcDataTimeString)
            ReferenceCountUtil.release(proxyMessage)
            return
        }
        if (ProxyMessageBodyType.TARGET_CHANNEL_CLOSE === proxyMessage.body.bodyType) {
            agentChannelContext.close()
            proxyChannelContext.close()
            return
        }
        if (ProxyMessageBodyType.CONNECT_SUCCESS === proxyMessage.body.bodyType) {
            val isHttps = proxyChannel.attr(HTTP_CONNECTION_IS_HTTPS).get()
            if (isHttps) {
                //HTTPS
                val okResponse =
                    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                agentChannelContext.channel().writeAndFlush(okResponse)
                    .addListener(ChannelFutureListener { agentOkResponseFuture ->
                        agentOkResponseFuture.channel().pipeline().apply {
                            if (this[HttpServerCodec::class.java.name] != null) {
                                remove(HttpServerCodec::class.java.name)
                            }
                            if (this[HttpObjectAggregator::class.java.name] != null) {
                                remove(HttpObjectAggregator::class.java.name)
                            }
                        }
                    })
                return
            }
            //HTTP
            val msg = proxyChannel.attr(HTTP_MESSAGE).get()
            val httpConnectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
            writeAgentMessageToProxy(AgentMessageBodyType.DATA,
                this.agentConfiguration.userToken,
                proxyChannelContext.channel(), httpConnectionInfo.host,
                httpConnectionInfo.port,
                msg, agentChannelContext.channel().id().asLongText(),
                MessageBodyEncryptionType.random()) {
                if (!it.isSuccess) {
                    ChannelInfoCache.removeChannelInfo(
                        agentChannelContext.channel().id().asLongText())
                    agentChannelContext.close()
                    proxyChannelContext.close()
                    logger.debug(
                        "Fail to send connect message from agent to proxy, clientChannelId=$agentChannelContext.channel().id().asLongText(), " +
                            "targetHost=${httpConnectionInfo.host}, targetPort =${httpConnectionInfo.port}",
                        it.cause())
                    return@writeAgentMessageToProxy
                }
            }
            return
        }
        proxyChannelContext.fireChannelRead(proxyMessage)
    }
}
