package com.ppaass.agent

import com.ppaass.kt.common.JSON_OBJECT_MAPPER
import org.apache.commons.io.FileUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import java.nio.file.Path
import java.util.*

data class AgentDynamicConfiguration(
    var userToken: String?,
    var port: Int?,
    var proxyHost: String?,
    var proxyPort: Int?,
)

@ConstructorBinding
@ConfigurationProperties("ppaass.agent")
class AgentConfiguration(
    var userToken: String?,
    var port: Int?,
    var proxyHost: String?,
    var proxyPort: Int?,
    var defaultLocal: Locale?,
    val agentTcpMasterThreadNumber: Int,
    val agentTcpWorkerThreadNumber: Int,
    val agentTcpSoBacklog: Int,
    val agentTcpSoLinger: Int,
    val agentTcpSoRcvbuf: Int,
    val agentTcpSoSndbuf: Int,
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
            this.port = agentDynamicConfiguration.port ?: this.port
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
            port = this.port,
            proxyHost = this.proxyHost,
            proxyPort = this.proxyPort
        )
        JSON_OBJECT_MAPPER.writeValue(agentDynamicConfigurationFile, agentDynamicConfiguration)
    }
}

@Configuration
private class Configure(private val agentConfiguration: AgentConfiguration) {
}
