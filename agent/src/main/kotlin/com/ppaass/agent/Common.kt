package com.ppaass.agent

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.ppaass.kt.common.JSON_OBJECT_MAPPER
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.AttributeKey
import org.apache.commons.io.FileUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import java.nio.file.Path
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AgentDynamicConfiguration(
    @JsonProperty(required = false)
    var userToken: String?,
    @JsonProperty(required = false)
    var tcpPort: Int?,
    @JsonProperty(required = false)
    var proxyHost: String?,
    @JsonProperty(required = false)
    var proxyPort: Int?,
)

@ConstructorBinding
@ConfigurationProperties("ppaass.agent")
class AgentConfiguration(
    var userToken: String?,
    var tcpPort: Int?,
    var proxyHost: String?,
    var proxyPort: Int?,
    var defaultLocal: Locale?,
    val agentTcpMasterThreadNumber: Int,
    val agentTcpWorkerThreadNumber: Int,
    val agentUdpThreadNumber: Int,
    val agentTcpSoBacklog: Int,
    val agentTcpSoLinger: Int,
    val agentTcpSoRcvbuf: Int,
    val agentTcpSoSndbuf: Int,
    val agentToProxyTcpChannelConnectRetry: Int,
    val agentToProxyTcpChannelWriteRetry: Int,
    val proxyTcpThreadNumber: Int,
    val proxyTcpConnectionTimeout: Int,
    val proxyTcpSoLinger: Int,
    val proxyTcpSoRcvbuf: Int,
    val proxyTcpSoSndbuf: Int,
    val proxyTcpCompressEnable: Boolean,
    agentPrivateKeyFile: Resource,
    proxyPublicKeyFile: Resource,
) {
    companion object {
        private const val USER_CONFIGURATION_FILE_NAME = ".ppaass"
        private const val USER_HOME_PROPERTY = "user.home"
    }

    var proxyPublicKey = FileUtils.readFileToString(proxyPublicKeyFile.file, Charsets.UTF_8)
    var agentPrivateKey = FileUtils.readFileToString(agentPrivateKeyFile.file, Charsets.UTF_8)

    init {
        val userDirectory = System.getProperty(USER_HOME_PROPERTY)
        val agentDynamicConfigurationFilePath = Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME)
        val agentDynamicConfigurationFile = agentDynamicConfigurationFilePath.toFile()
        if (agentDynamicConfigurationFile.exists()) {
            val agentDynamicConfiguration =
                JSON_OBJECT_MAPPER.readValue(agentDynamicConfigurationFile,
                    AgentDynamicConfiguration::class.java)
            this.tcpPort = agentDynamicConfiguration.tcpPort ?: this.tcpPort
            this.proxyHost = agentDynamicConfiguration.proxyHost ?: this.proxyHost
            this.proxyPort = agentDynamicConfiguration.proxyPort ?: this.proxyPort
            this.userToken = agentDynamicConfiguration.userToken ?: this.userToken
        }
    }

    fun save() {
        val userDirectory = System.getProperty(USER_HOME_PROPERTY)
        val agentDynamicConfigurationFilePath = Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME)
        val agentDynamicConfigurationFile = agentDynamicConfigurationFilePath.toFile()
        if (agentDynamicConfigurationFile.exists()) {
            agentDynamicConfigurationFile.delete()
        }
        agentDynamicConfigurationFile.createNewFile()
        val agentDynamicConfiguration = AgentDynamicConfiguration(
            userToken = this.userToken,
            tcpPort = this.tcpPort,
            proxyHost = this.proxyHost,
            proxyPort = this.proxyPort
        )
        JSON_OBJECT_MAPPER.writeValue(agentDynamicConfigurationFile, agentDynamicConfiguration)
    }
}

enum class ChannelProtocolCategory {
    HTTP, SOCKS
}

internal val CHANNEL_PROTOCOL_CATEGORY: AttributeKey<ChannelProtocolCategory> =
    AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE")

@Configuration
private class Configure(private val agentConfiguration: AgentConfiguration) {
    @Bean
    fun proxyTcpLoopGroup(): EventLoopGroup = NioEventLoopGroup(
        agentConfiguration.proxyTcpThreadNumber)
}
