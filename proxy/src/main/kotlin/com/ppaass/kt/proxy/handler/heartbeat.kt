package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.message.MessageEncryptionType
import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBodyType
import com.ppaass.kt.common.message.proxyMessageBody
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal class HeartbeatChannelHandler : ChannelInboundHandlerAdapter() {
    override fun userEventTriggered(proxyContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(proxyContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            return
        }
        val utcDateTime = ZonedDateTime.now()
        val utcDataTimeString = utcDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val proxyMessage =
                ProxyMessage(UUID.randomUUID().toString(), MessageEncryptionType.BASE64_AES,
                        proxyMessageBody(ProxyMessageBodyType.HEARTBEAT, UUID.randomUUID().toString()) {
                            originalData = utcDataTimeString.toByteArray()
                        })
        proxyContext.channel().writeAndFlush(proxyMessage)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
        ReferenceCountUtil.release(evt)
    }
}