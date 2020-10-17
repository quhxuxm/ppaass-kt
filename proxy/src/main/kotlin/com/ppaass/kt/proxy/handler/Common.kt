package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.handler.codec.LengthFieldPrepender

internal val lengthFieldPrepender = LengthFieldPrepender(4)
internal val heartbeatHandler = HeartbeatHandler()

