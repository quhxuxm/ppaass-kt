package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey

internal val AGENT_CHANNEL_CONTEXT =
    AttributeKey.valueOf<ChannelHandlerContext>("AGENT_CHANNEL_CONTEXT")
internal val HTTP_CONNECTION_INFO = AttributeKey.valueOf<HttpConnectionInfo>("HTTP_CONNECTION_INFO")
internal val HTTP_CONNECTION_KEEP_ALIVE =
    AttributeKey.valueOf<Boolean>("HTTP_CONNECTION_KEEP_ALIVE")
internal val PROXY_CHANNEL_ACTIVE_CALLBACK =
    AttributeKey.valueOf<(proxyChannelContext: ChannelHandlerContext) -> Unit>(
        "PROXY_CHANNEL_ACTIVE_CALLBACK")

