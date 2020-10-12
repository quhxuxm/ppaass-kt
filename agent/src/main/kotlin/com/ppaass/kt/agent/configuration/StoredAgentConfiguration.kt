package com.ppaass.kt.agent.configuration

data class StoredAgentConfiguration(
    var userToken: String? = null,
    var proxyAddress: String? = null,
    var proxyPort: Int? = null,
    var port: Int? = null
)

fun storedAgentConfiguration(block: StoredAgentConfiguration.() -> Unit): StoredAgentConfiguration {
    val result = StoredAgentConfiguration()
    block(result)
    return result
}
