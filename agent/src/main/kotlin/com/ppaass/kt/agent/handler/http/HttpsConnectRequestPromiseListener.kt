package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import mu.KotlinLogging

internal class HttpsConnectRequestPromiseListener(
        private val agentChannelContext: ChannelHandlerContext) :
        GenericFutureListener<Future<Channel>> {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val resourceClearHandler = ResourceClearHandler()
    }

    override fun operationComplete(promiseFuture: Future<Channel>) {
        if (!promiseFuture.isSuccess) {
            return
        }
        with(this.agentChannelContext.pipeline()) {
            addLast(resourceClearHandler)
        }
        val okResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        agentChannelContext.writeAndFlush(okResponse)
                .addListener(ChannelFutureListener { okResponseFuture ->
                    with(okResponseFuture.channel().pipeline()) {
                        remove(HttpServerCodec::class.java.name)
                        remove(HttpObjectAggregator::class.java.name)
                        remove(ChunkedWriteHandler::class.java.name)
                    }
                })
    }
}
