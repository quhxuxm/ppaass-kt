package com.ppaass.kt.agent.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*

@Service
class AgentConfiguration(val staticAgentConfiguration: StaticAgentConfiguration) {
    final val objectMapper: ObjectMapper

    companion object {
        private val logger = LoggerFactory.getLogger(AgentConfiguration::class.java)
        private const val USER_CONFIGURATION_FILE_NAME = ".ppaass"
        private const val USER_HOME_PROPERTY = "user.home"
        private val RANDOM_USER_TOKEN = UUID.randomUUID().toString().replace("-", "")
    }

    final var userToken: String = RANDOM_USER_TOKEN
    final var proxyAddress: String
    final var proxyPort: Int = -1
    final var port: Int = -1
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
