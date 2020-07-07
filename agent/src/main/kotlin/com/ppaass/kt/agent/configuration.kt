package com.ppaass.kt.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*

@ConfigurationProperties(prefix = "ppaass.agent")
@Service
class StaticAgentConfiguration {
    var masterIoEventThreadNumber = 0
    var workerIoEventThreadNumber = 0
    var businessEventThreadNumber = 0
    var proxyDataTransferIoEventThreadNumber = 0
    var soBacklog = 0
    var port = 0
    var proxyServerAddress: String? = null
    var proxyServerPort = 0
    var proxyConnectionTimeout = 0
    var defaultLocale: Locale? = Locale.getDefault()
    var clientConnectionIdleSeconds = 0
    var proxyServerReceiveDataAverageBufferMinSize = 0
    var proxyServerReceiveDataAverageBufferInitialSize = 0
    var proxyServerReceiveDataAverageBufferMaxSize = 0
    var proxyServerSoRcvbuf = 0
}

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

@Service
class AgentConfiguration(final val staticAgentConfiguration: StaticAgentConfiguration) {
    final val objectMapper: ObjectMapper

    companion object {
        private val logger = LoggerFactory.getLogger(AgentConfiguration::class.java)
        private const val USER_CONFIGURATION_FILE_NAME = ".ppaass"
        private const val USER_HOME_PROPERTY = "user.home"
        private val RANDOM_USER_TOKEN = UUID.randomUUID().toString().replace("-", "")
    }

    final var userToken: String = RANDOM_USER_TOKEN
    final var proxyAddress: String
    final var proxyPort: Int = 0
    final var port: Int? = null
    final var messageBodyEncryptionType: MessageBodyEncryptionType? = null

    init {
        this.objectMapper = jacksonObjectMapper()
        this.proxyAddress = staticAgentConfiguration.proxyServerAddress ?: "localhost"
        this.proxyPort = staticAgentConfiguration.proxyServerPort
        this.port = staticAgentConfiguration.port
        this.messageBodyEncryptionType = MessageBodyEncryptionType.random()
        val userDirectory = System.getProperty(USER_HOME_PROPERTY)
        val configurationFilePath = Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME)
        val file = File(configurationFilePath.toUri())
        if (file.exists()) {
            val savedAgentConfiguration: StoredAgentConfiguration =
                    objectMapper.readValue(file, StoredAgentConfiguration::class.java)
            this.port = savedAgentConfiguration.port ?: this.port
            this.proxyAddress = savedAgentConfiguration.proxyAddress ?: this.proxyAddress
            this.proxyPort = savedAgentConfiguration.proxyPort ?: this.proxyPort
            this.userToken = savedAgentConfiguration.userToken ?: RANDOM_USER_TOKEN
        }
    }

    fun save() {
        val userDirectory = System.getProperty(USER_HOME_PROPERTY)
        val configurationFilePath =
                Path.of(userDirectory, USER_CONFIGURATION_FILE_NAME)
        val file = File(configurationFilePath.toUri())
        if (file.exists()) {
            if (!file.delete()) {
                logger.error(
                        "Fail to save configuration because of can not delete previous configuration file.")
                return
            }
        }
        try {
            if (!file.createNewFile()) {
                logger.error(
                        "Fail to save configuration because of can not create configuration file.")
                return
            }
        } catch (e: IOException) {
            logger.error("Fail to save configuration because of can not create configuration file.",
                    e)
        }
        try {
            val storedAgentConfiguration = storedAgentConfiguration {
                userToken = this@AgentConfiguration.userToken
                proxyPort = this@AgentConfiguration.proxyPort
                proxyAddress = this@AgentConfiguration.proxyAddress
                port = this@AgentConfiguration.port
            }
            objectMapper.writeValue(file, storedAgentConfiguration)
        } catch (e: IOException) {
            logger.error("Fail to save configuration because of exception.", e)
        }
    }
}
