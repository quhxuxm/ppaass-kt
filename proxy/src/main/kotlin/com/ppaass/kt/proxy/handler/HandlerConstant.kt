package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey

internal val PROXY_CHANNEL_CONTEXT =
    AttributeKey.valueOf<ChannelHandlerContext>("PROXY_CHANNEL_CONTEXT")
internal val TARGET_CHANNEL =
    AttributeKey.valueOf<Channel>("TARGET_CHANNEL")
internal val AGENT_CONNECT_MESSAGE = AttributeKey.valueOf<AgentMessage>("AGENT_CONNECT_MESSAGE")
internal val HANDLERS_TO_REMOVE_AFTER_TARGET_ACTIVE =
    AttributeKey.valueOf<List<ChannelHandler>>("HANDLERS_TO_REMOVE_ON_TARGET_ACTIVE")
