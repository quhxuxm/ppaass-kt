package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.slf4j.LoggerFactory

internal class HttpsConnectRequestPromiseListener(
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