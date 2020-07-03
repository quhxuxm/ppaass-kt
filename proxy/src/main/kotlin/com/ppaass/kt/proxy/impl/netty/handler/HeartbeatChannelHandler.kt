package com.ppaass.kt.proxy.impl.netty.handler

import com.ppaass.kt.common.message.MessageEncryptionType
import com.ppaass.kt.common.message.ProxyMessageBodyType
import com.ppaass.kt.common.message.proxyMessage
import com.ppaass.kt.common.message.proxyMessageBody
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HeartbeatChannelHandler : ChannelInboundHandlerAdapter() {
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
        val proxyMessage = proxyMessage {
            secureToken = UUID.randomUUID().toString()
            encryptionType = MessageEncryptionType.BASE64_AES
            body = proxyMessageBody {
                id = UUID.randomUUID().toString()
                bodyType = ProxyMessageBodyType.HEARTBEAT
                targetAddress = null
                targetPort = null
                originalData = null
            }
        }
//        val proxyMessage = ProxyMessage(ProxyMessage.Status.HEARTBEAT, null, null, utcDataTimeString.toByteArray())
//        val messageWrapper: MessageWrapper<ProxyMessage> = MessageWrapper(UUID.randomUUID().toString(),
//                EncryptionUtil.INSTANCE.randomEncryptionType(), proxyMessage)
//        val combineWrapperAndProxyMessageBo = CombineWrapperAndProxyMessageBo(messageWrapper, proxyMessage)
//        proxyContext.channel().writeAndFlush(combineWrapperAndProxyMessageBo)
//                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
//        ReferenceCountUtil.release(evt)
    }
}