package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey

internal val PROXY_CHANNEL_CONTEXT = AttributeKey.valueOf<ChannelHandlerContext>("PROXY_CHANNEL_CONTEXT")
internal val TARGET_CHANNEL_CONTEXT = AttributeKey.valueOf<ChannelHandlerContext>("TARGET_CHANNEL_CONTEXT")
internal val AGENT_CONNECT_MESSAGE = AttributeKey.valueOf<AgentMessage>("AGENT_CONNECT_MESSAGE")
internal val CONNECTION_KEEP_ALIVE = AttributeKey.valueOf<Boolean>("CONNECTION_KEEP_ALIVE")
