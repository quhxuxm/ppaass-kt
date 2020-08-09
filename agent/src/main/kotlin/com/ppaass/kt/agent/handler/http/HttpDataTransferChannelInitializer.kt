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
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseDecoder
import io.netty.util.concurrent.EventExecutorGroup
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory

internal class HttpDataTransferChannelInitializer(private val agentChannel: Channel,
                                                  private val executorGroup: EventExecutorGroup,
                                                  private val httpConnectionInfo: HttpConnectionInfo,
                                                  private val clientChannelId: String,
                                                  private val proxyChannelConnectedPromise: Promise<Channel>,
                                                  private val agentConfiguration: AgentConfiguration) :
        ChannelInitializer<SocketChannel>() {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpDataTransferChannelInitializer::class.java)
        private val discardProxyHeartbeatHandler = DiscardProxyHeartbeatHandler()
        private val lengthFieldPrepender = LengthFieldPrepender(4)
        private val resourceClearHandler = ResourceClearHandler()
    }

    override fun initChannel(httpProxyChannel: SocketChannel) {
        logger.debug("Initialize HTTP data transfer channel, clientChannelId={}",
                clientChannelId)
        with(httpProxyChannel.pipeline()) {
            addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                    0, 4, 0,
                    4))
            addLast(ProxyMessageDecoder())
            addLast(discardProxyHeartbeatHandler)
            addLast(ExtractProxyMessageOriginalDataDecoder())
            addLast(HttpResponseDecoder())
            addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
            addLast(executorGroup,
                    TransferDataFromProxyToAgentHandler(agentChannel,
                            httpConnectionInfo.host, httpConnectionInfo.port,
                            clientChannelId,
                            agentConfiguration, proxyChannelConnectedPromise))
            addLast(resourceClearHandler)
            addLast(lengthFieldPrepender)
            addLast(AgentMessageEncoder())
        }
    }
}
