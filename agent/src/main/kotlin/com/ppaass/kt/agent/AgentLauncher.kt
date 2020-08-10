package com.ppaass.kt.agent;

import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import java.awt.EventQueue

/**
 * The proxy launcher
 */
@SpringBootApplication
@EnableConfigurationProperties
class AgentLauncher {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun launch(vararg arguments: String) {
        logger.info("Begin to launch agent.")
        val context = SpringApplicationBuilder(AgentLauncher::class.java)
                .headless(false).run(*arguments)
        EventQueue.invokeLater {
            val mainFrame = context.getBean(MainFrame::class.java)
            mainFrame.start()
        }
    }
}

fun main(args: Array<String>) {
    val agentLauncher = AgentLauncher()
    agentLauncher.launch(*args)
}
