package com.ppaass.agent.ui

import com.ppaass.agent.Agent
import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.generateUuid
import mu.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.awt.AWTException
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

@Service
class MainFrame(private val messageSource: MessageSource,
                private val agentConfiguration: AgentConfiguration,
                private val agent: Agent) : JFrame() {
    private companion object {
        private val logger = KotlinLogging.logger { }
        private val TOKEN_LABEL_MESSAGE_KEY = "mainFrame.tokenLabel"
        private val AGENT_PORT_LABEL_MESSAGE_KEY = "mainFrame.agentPortLabel"
        private val PROXY_ADDRESS_LABEL_MESSAGE_KEY = "mainFrame.proxyAddressLabel"
        private val PROXY_PORT_LABEL_MESSAGE_KEY = "mainFrame.proxyPortLabel"
        private val SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY = "mainFrame.systemTray.tooltip"
        private val STATUS_LABEL_DEFAULT_MESSAGE_KEY = "mainFrame.statusLabel.default"
        private val BUTTON_START_PROXY_MESSAGE_KEY = "mainFrame.button.startProxy"
        private val BUTTON_STOP_PROXY_MESSAGE_KEY = "mainFrame.button.stopProxy"
        private val STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY =
            "mainFrame.status.tokenValidationFail"
        private val STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY = "mainFrame.status.portValidationFail"
        private val STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY =
            "mainFrame.status.proxyPortValidationFail"
        private val STATUS_PROXY_IS_RUNNING_MESSAGE_KEY = "mainFrame.status.proxyIsRunning"
        private val STATUS_AGENT_START_FAIL_MESSAGE_KEY = "mainFrame.status.agentStartFail"
        private val LOGO_BLACK = "icons/logo_black.png"
        private val LOGO_WHITE = "icons/logo_white.png"
        private val PANEL_WIDTH = 500
    }

    private fun initialize() {
        val contentPanel = initializeContent()
        this.contentPane = contentPanel
        defaultCloseOperation = EXIT_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                stop()
            }
        })
        this.iconImage = Toolkit.getDefaultToolkit()
            .getImage(MainFrame::class.java.classLoader.getResource(LOGO_BLACK))
        this.addWindowStateListener { e: WindowEvent ->
            if (e.newState == ICONIFIED || e.newState == 7) {
                this.isVisible = false
            }
        }
        if (SystemTray.isSupported()) {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit()
                .getImage(MainFrame::class.java.classLoader.getResource(LOGO_WHITE))
            val trayIcon = TrayIcon(image,
                getMessage(SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY))
            trayIcon.isImageAutoSize = true
            trayIcon.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        this@MainFrame.isVisible = !this@MainFrame.isShowing
                        this@MainFrame.extendedState = NORMAL
                        toFront()
                        return
                    }
                    System.exit(0)
                }
            })
            try {
                tray.add(trayIcon)
            } catch (e: AWTException) {
                logger.error("Fail to add system tray icon because of exception.", e)
            }
        }
        this.isResizable = false
        pack()
    }

    private fun preVerifyToken(tokenInput: JTextArea): Boolean {
        val inputToken = tokenInput.text
        return !StringUtils.isEmpty(inputToken)
    }

    private fun initializeContent(): JPanel {
        val contentPanel = JPanel()
        val contentPanelLayout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.layout = contentPanelLayout
        contentPanel.border = EmptyBorder(10, 10, 10, 10)
        val tokenLabelPanel = JPanel()
        tokenLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        tokenLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        tokenLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val tokenLabel = JLabel(this.getMessage(TOKEN_LABEL_MESSAGE_KEY))
        tokenLabelPanel.add(tokenLabel)
        contentPanel.add(tokenLabelPanel)
        val tokenTextFieldPanel = JPanel()
        tokenTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 100)
        tokenTextFieldPanel.layout = BoxLayout(tokenTextFieldPanel, BoxLayout.Y_AXIS)
        tokenTextFieldPanel.border = EmptyBorder(5, 0, 5, 0)
        val tokenInput = JTextArea()
        tokenInput.text = this.agentConfiguration.userToken
        tokenInput.lineWrap = true
        tokenInput.disabledTextColor = Color(200, 200, 200)
        val tokenInputScrollPane = JScrollPane(tokenInput)
        tokenInputScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        tokenInputScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        tokenTextFieldPanel.add(tokenInputScrollPane)
        contentPanel.add(tokenTextFieldPanel)
        val agentPortLabelPanel = JPanel()
        agentPortLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        agentPortLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        agentPortLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val agentPortLabel = JLabel(this.getMessage(AGENT_PORT_LABEL_MESSAGE_KEY))
        agentPortLabelPanel.add(agentPortLabel)
        contentPanel.add(agentPortLabelPanel)
        val agentPortTextFieldPanel = JPanel()
        agentPortTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        agentPortTextFieldPanel.layout = BoxLayout(agentPortTextFieldPanel, BoxLayout.Y_AXIS)
        agentPortTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val agentPortInput = JTextField()
        agentPortInput.text = this.agentConfiguration.port.toString()
        agentPortInput.disabledTextColor = Color(200, 200, 200)
        agentPortTextFieldPanel.add(agentPortInput)
        contentPanel.add(agentPortTextFieldPanel)
        val proxyAddressLabelPanel = JPanel()
        proxyAddressLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        proxyAddressLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        proxyAddressLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val proxyAddressLabel = JLabel(this.getMessage(PROXY_ADDRESS_LABEL_MESSAGE_KEY))
        proxyAddressLabelPanel.add(proxyAddressLabel)
        contentPanel.add(proxyAddressLabelPanel)
        val proxyAddressTextFieldPanel = JPanel()
        proxyAddressTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        proxyAddressTextFieldPanel.layout = BoxLayout(proxyAddressTextFieldPanel, BoxLayout.Y_AXIS)
        proxyAddressTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val proxyAddressInput = JTextField()
        proxyAddressInput.text = this.agentConfiguration.proxyHost
        proxyAddressInput.disabledTextColor = Color(200, 200, 200)
        proxyAddressTextFieldPanel.add(proxyAddressInput)
        contentPanel.add(proxyAddressTextFieldPanel)
        val proxyPortLabelPanel = JPanel()
        proxyPortLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        proxyPortLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        proxyPortLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val proxyPortLabel = JLabel(this.getMessage(PROXY_PORT_LABEL_MESSAGE_KEY))
        proxyPortLabelPanel.add(proxyPortLabel)
        contentPanel.add(proxyPortLabelPanel)
        val proxyPortTextFieldPanel = JPanel()
        proxyPortTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        proxyPortTextFieldPanel.layout = BoxLayout(proxyPortTextFieldPanel, BoxLayout.Y_AXIS)
        proxyPortTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val proxyPortInput = JTextField()
        proxyPortInput.text = this.agentConfiguration.proxyPort.toString()
        proxyPortInput.disabledTextColor = Color(200, 200, 200)
        proxyPortTextFieldPanel.add(proxyPortInput)
        contentPanel.add(proxyPortTextFieldPanel)
        val buttonPanelLayout = GridLayout(1, 3, 10, 0)
        val buttonPanel = JPanel(buttonPanelLayout)
        buttonPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        val statusLabel = JLabel(this.getMessage(STATUS_LABEL_DEFAULT_MESSAGE_KEY))
        val stopAllProxyBtn = JButton(this.getMessage(BUTTON_STOP_PROXY_MESSAGE_KEY))
        val startProxyBtn = JButton(
            this.getMessage(BUTTON_START_PROXY_MESSAGE_KEY))

        stopAllProxyBtn.isEnabled = false
        stopAllProxyBtn.addActionListener { e: ActionEvent? ->
            try {
                agent.stop()
            } catch (e1: Exception) {
                logger.error("Fail to stop agent because of exception.", e1)
            }
            statusLabel.text = getMessage(STATUS_LABEL_DEFAULT_MESSAGE_KEY)
            tokenInput.isEnabled = true
            tokenInput.isFocusable = true
            tokenInput.isEditable = true
            agentPortInput.isEnabled = true
            agentPortInput.isFocusable = true
            agentPortInput.isEditable = true
            proxyAddressInput.isEnabled = true
            proxyAddressInput.isFocusable = true
            proxyAddressInput.isEditable = true
            proxyPortInput.isEnabled = true
            proxyPortInput.isFocusable = true
            proxyPortInput.isEditable = true
            startProxyBtn.isEnabled = true
            stopAllProxyBtn.isEnabled = false
        }
        startProxyBtn.addActionListener { e: ActionEvent? ->
            if (!this@MainFrame.preVerifyToken(tokenInput)) {
                statusLabel.text = this.getMessage(STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.userToken = tokenInput.text
            if (this.agentConfiguration.userToken.isNullOrBlank()) {
                this.agentConfiguration.userToken = generateUuid()
            }
            var port = -1
            try {
                port = agentPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = this.getMessage(STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.port = port
            var proxyPort = -1
            try {
                proxyPort = proxyPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = this.getMessage(STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.proxyPort = proxyPort
            this.agentConfiguration.proxyHost = proxyAddressInput.text
            try {
                agent.start()
            } catch (e1: Exception) {
                statusLabel.text = this.getMessage(STATUS_AGENT_START_FAIL_MESSAGE_KEY)
                logger.error("Fail to start http agent because of exception.", e1)
                return@addActionListener
            }
            statusLabel.text = this.getMessage(STATUS_PROXY_IS_RUNNING_MESSAGE_KEY)
            tokenInput.isEditable = false
            tokenInput.isFocusable = false
            tokenInput.isEnabled = false
            agentPortInput.isEditable = false
            agentPortInput.isFocusable = false
            agentPortInput.isEnabled = false
            proxyAddressInput.isEditable = false
            proxyAddressInput.isFocusable = false
            proxyAddressInput.isEnabled = false
            proxyPortInput.isEditable = false
            proxyPortInput.isFocusable = false
            proxyPortInput.isEnabled = false
            stopAllProxyBtn.isEnabled = true
            startProxyBtn.isEnabled = false
            this.agentConfiguration.save()
        }

        buttonPanel.add(startProxyBtn)
        buttonPanel.add(stopAllProxyBtn)
        buttonPanel.border = EmptyBorder(0, 0, 10, 0)
        contentPanel.add(buttonPanel)
        val statusPanel = JPanel(CardLayout())
        statusPanel.preferredSize = Dimension(PANEL_WIDTH, 20)
        statusPanel.add(statusLabel)
        contentPanel.add(statusPanel)
        return contentPanel
    }

    private fun getMessage(statusTokenValidationFailMessageKey: String): String {
        var locale = Locale.getDefault()
        if (this.agentConfiguration.defaultLocal != null) {
            locale = this.agentConfiguration.defaultLocal
        }
        return this.messageSource
            .getMessage(statusTokenValidationFailMessageKey, null, locale!!)
    }

    fun start() {
        this.initialize()
        this.isVisible = true
    }

    fun stop() {
        this.isVisible = false
        this.agent.stop()
    }
}
