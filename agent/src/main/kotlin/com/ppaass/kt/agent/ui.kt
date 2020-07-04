package com.ppaass.kt.agent

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import javax.swing.JFrame

@Service
class MainFrame(applicationContext: ApplicationContext, messageSource: MessageSource) : JFrame() {
    companion object {
        private val logger = LoggerFactory.getLogger(MainFrame::class.java)
        private const val TOKEN_LABEL_MESSAGE_KEY = "mainFrame.tokenLabel"
        private const val AGENT_PORT_LABEL_MESSAGE_KEY = "mainFrame.agentPortLabel"
        private const val PROXY_ADDRESS_LABEL_MESSAGE_KEY = "mainFrame.proxyAddressLabel"
        private const val PROXY_PORT_LABEL_MESSAGE_KEY = "mainFrame.proxyPortLabel"
        private const val SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY = "mainFrame.systemTray.tooltip"
        private const val STATUS_LABEL_DEFAULT_MESSAGE_KEY = "mainFrame.statusLabel.default"
        private const val BUTTON_START_HTTP_PROXY_MESSAGE_KEY = "mainFrame.button.startHttpProxy"
        private const val BUTTON_START_SOCKS5_PROXY_MESSAGE_KEY = "mainFrame.button.startSocks5Proxy"
        private const val BUTTON_STOP_PROXY_MESSAGE_KEY = "mainFrame.button.stopProxy"
        private const val STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY = "mainFrame.status.tokenValidationFail"
        private const val STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY = "mainFrame.status.portValidationFail"
        private const val STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY =
                "mainFrame.status.proxyPortValidationFail"
        private const val STATUS_HTTP_PROXY_IS_RUNNING_MESSAGE_KEY = "mainFrame.status.httpProxyIsRunning"
        private const val STATUS_AGENT_START_FAIL_MESSAGE_KEY = "mainFrame.status.agentStartFail"
        private const val STATUS_SOCKS5_PROXY_IS_RUNNING_MESSAGE_KEY = "mainFrame.status.socks5ProxyIsRunning"
        private const val LOGO_BLACK = "icons/logo_black.png"
        private const val LOGO_WHITE = "icons/logo_white.png"
        private const val PANEL_WIDTH = 500
    }
}