package com.ppaass.agent.handler.http

import io.netty.channel.Channel
import io.netty.util.AttributeKey

@JvmField
internal val HTTP_CONNECTION_INFO: AttributeKey<HttpConnectionInfo> =
    AttributeKey.valueOf("HTTP_CONNECTION_INFO")

internal data class HttpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val isHttps: Boolean
) {
    var userToken: String? = null
    var proxyChannel: Channel? = null
    var agentChannel: Channel? = null
    var isKeepAlive: Boolean = true
    var httpMessageCarriedOnConnectTime: Any? = null
    override fun toString(): String {
        return "HttpConnectionInfo(targetHost='${
            targetHost
        }', targetPort=${
            targetPort
        }, isHttps=${isHttps}, userToken=${userToken}, proxyChannel=${
            proxyChannel?.id()?.asLongText()
        }, agentChannel=${
            agentChannel?.id()?.asLongText()
        }, isKeepAlive=${isKeepAlive}, httpMessageCarriedOnConnectTime=${httpMessageCarriedOnConnectTime})"
    }
}
