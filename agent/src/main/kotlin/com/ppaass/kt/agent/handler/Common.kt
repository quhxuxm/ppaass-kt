package com.ppaass.kt.agent.handler

import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.handler.codec.LengthFieldPrepender

internal val discardProxyHeartbeatHandler = DiscardProxyHeartbeatHandler()
internal val lengthFieldPrepender = LengthFieldPrepender(4)
internal val resourceClearHandler = ResourceClearHandler()
