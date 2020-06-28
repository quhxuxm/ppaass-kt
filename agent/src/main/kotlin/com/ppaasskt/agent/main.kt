package com.ppaasskt.agent

import com.ppaasskt.agent.ui.MainFrame
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import java.awt.EventQueue

@SpringBootApplication
class Main;

val logger: Logger = LoggerFactory.getLogger(Main::class.java)
fun main(args: Array<String>) {
    logger.info("Begin to start ppass-kt agent...");
    val agentApplicationContext = SpringApplicationBuilder(Main::class.java).headless(false).run(*args);
    EventQueue.invokeLater {

    }
    EventQueue.invokeLater {
        val mainFrame: MainFrame = agentApplicationContext.getBean(MainFrame::class.java)
        Runtime.getRuntime().addShutdownHook(Thread(Runnable { mainFrame.getAgent().stop() }))
        mainFrame.start()
    }
    logger.info("Start ppass-kt agent success.");
}