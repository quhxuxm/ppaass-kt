package com.ppaass.kt.agent.handler.socks.v5

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.util.AttributeKey

internal val AGENT_CHANNEL_CONTEXT = AttributeKey.valueOf<ChannelHandlerContext>("AGENT_CHANNEL_CONTEXT")
internal val PROXY_CHANNEL_CONTEXT = AttributeKey.valueOf<ChannelHandlerContext>("PROXY_CHANNEL_CONTEXT")
internal val SOCKS_V5_COMMAND_REQUEST = AttributeKey.valueOf<Socks5CommandRequest>("SOCKS_V5_COMMAND_REQUEST")
internal val HANDLERS_TO_REMOVE_AFTER_PROXY_ACTIVE =
    AttributeKey.valueOf<List<ChannelHandler>>("HANDLERS_TO_REMOVE_AFTER_PROXY_ACTIVE")
