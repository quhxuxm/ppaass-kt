package com.ppaass.kt.proxy.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(proxyContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(proxyContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            return
        }
        proxyContext.close()
//        if (!proxyContext.channel().isActive) {
//            logger.info { "Close proxy channel ${proxyContext.channel().id().asLongText()} because it is not avtive." }
//            proxyContext.close()
//        }
//        val utcDateTime = ZonedDateTime.now()
//        val utcDataTimeString = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//        val messageId = UUID.randomUUID().toString().replace("-", "")
//        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.HEARTBEAT, messageId)
//        proxyMessageBody.originalData = utcDataTimeString.toByteArray()
//        val proxyMessage =
//                ProxyMessage(UUID.randomUUID().toString(), MessageBodyEncryptionType.random(), proxyMessageBody)
//        if (!proxyContext.channel().isActive) {
//            logger.error("Close proxy channel as it is not active on heartbeat time, proxyChannelId={}",
//                    proxyContext.channel().id().asLongText())
//            proxyContext.close()
//        }
//        proxyContext.channel().writeAndFlush(proxyMessage)
//                .addListener(ChannelFutureListener {
//                    if (!it.isSuccess) {
//                        val proxyChannelId = it.channel().id().asLongText()
//                        it.channel().close()
//                        logger.error("Close proxy channel as agent heartbeat fail, proxyChannelId={}", proxyChannelId)
//                    }
//                })
//        ReferenceCountUtil.release(evt)
    }
}
