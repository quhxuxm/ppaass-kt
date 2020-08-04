package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.common.DiscardProxyHeartbeatHandler
import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.EventExecutorGroup
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory

internal class HttpsDataTransferChannelInitializer(private val agentChannel: Channel,
                                                   private val executorGroup: EventExecutorGroup,
                                                   private val httpConnectionInfo: HttpConnectionInfo,
                                                   private val clientChannelId: String,
                                                   private val proxyChannelActivePromise: Promise<Channel>,
                                                   private val agentConfiguration: AgentConfiguration) :
        ChannelInitializer<SocketChannel>() {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpsDataTransferChannelInitializer::class.java)
    }

    override fun initChannel(httpsProxyChannel: SocketChannel) {
        logger.debug("Initialize HTTPS data transfer channel, clientChannelId={}",
                clientChannelId)
        with(httpsProxyChannel.pipeline()) {
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                    0, 4, 0,
                    4))
            addLast(ProxyMessageDecoder())
            addLast(DiscardProxyHeartbeatHandler(agentChannel))
            addLast(ExtractProxyMessageOriginalDataDecoder())
            addLast(executorGroup,
                    TransferDataFromProxyToAgentHandler(agentChannel,
                            httpConnectionInfo.host, httpConnectionInfo.port,
                            clientChannelId,
                            agentConfiguration, proxyChannelActivePromise))
            addLast(ResourceClearHandler())
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(AgentMessageEncoder())
        }
    }
}