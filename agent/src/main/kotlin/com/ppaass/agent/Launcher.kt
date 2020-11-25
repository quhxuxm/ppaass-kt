package com.ppaass.agent

import com.ppaass.agent.ui.MainFrame
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import java.awt.EventQueue

private val logger = KotlinLogging.logger { }

@SpringBootApplication
@EnableConfigurationProperties(AgentConfiguration::class)
class Launcher

fun main(args: Array<String>) {
    val context: ApplicationContext =
        SpringApplicationBuilder(Launcher::class.java)
            .headless(false).run(*args)
    EventQueue.invokeLater {
        val mainFrame = context.getBean(MainFrame::class.java)
        mainFrame.start()
    }
}
