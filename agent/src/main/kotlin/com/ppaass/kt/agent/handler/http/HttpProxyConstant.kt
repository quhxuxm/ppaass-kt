package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey

internal val AGENT_CHANNEL_CONTEXT =
    AttributeKey.valueOf<ChannelHandlerContext>("AGENT_CHANNEL_CONTEXT")
internal val HTTP_CONNECTION_INFO = AttributeKey.valueOf<HttpConnectionInfo>("HTTP_CONNECTION_INFO")
internal val HTTP_MESSAGE = AttributeKey.valueOf<Any>("HTTP_MESSAGE")
internal val HTTP_CONNECTION_IS_HTTPS= AttributeKey.valueOf<Boolean>("HTTP_CONNECTION_IS_HTTPS")
internal val HTTP_CONNECTION_KEEP_ALIVE =
    AttributeKey.valueOf<Boolean>("HTTP_CONNECTION_KEEP_ALIVE")


