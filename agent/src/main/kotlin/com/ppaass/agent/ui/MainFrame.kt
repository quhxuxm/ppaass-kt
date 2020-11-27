package com.ppaass.agent.ui

import com.ppaass.agent.Agent
import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.generateUuid
import mu.KotlinLogging
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
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
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

@Service
internal class MainFrame(private val messageSource: MessageSource,
                         private val agentConfiguration: AgentConfiguration,
                         private val agent: Agent) : JFrame() {
    private companion object {
        private val logger = KotlinLogging.logger { }
        private val TOKEN_LABEL_MESSAGE_KEY = "mainFrame.tokenLabel"
        private val AGENT_TCP_PORT_LABEL_MESSAGE_KEY = "mainFrame.agentTcpPortLabel"
        private val PROXY_ADDRESS_LABEL_MESSAGE_KEY = "mainFrame.proxyAddressLabel"
        private val PROXY_PORT_LABEL_MESSAGE_KEY = "mainFrame.proxyPortLabel"
        private val SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY = "mainFrame.systemTray.tooltip"
        private val STATUS_LABEL_DEFAULT_MESSAGE_KEY = "mainFrame.statusLabel.default"
        private val BUTTON_START_PROXY_MESSAGE_KEY = "mainFrame.button.startProxy"
        private val BUTTON_ADJUST_LOGGER_MESSAGE_KEY = "mainFrame.button.adjustLogger"
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
        val agentTcpPortLabelPanel = JPanel()
        agentTcpPortLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        agentTcpPortLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        agentTcpPortLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val agentTcpPortLabel = JLabel(this.getMessage(AGENT_TCP_PORT_LABEL_MESSAGE_KEY))
        agentTcpPortLabelPanel.add(agentTcpPortLabel)
        contentPanel.add(agentTcpPortLabelPanel)
        val agentTcpPortTextFieldPanel = JPanel()
        agentTcpPortTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        agentTcpPortTextFieldPanel.layout = BoxLayout(agentTcpPortTextFieldPanel, BoxLayout.Y_AXIS)
        agentTcpPortTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val agentTcpPortInput = JTextField()
        agentTcpPortInput.text = this.agentConfiguration.tcpPort.toString()
        agentTcpPortInput.disabledTextColor = Color(200, 200, 200)
        agentTcpPortTextFieldPanel.add(agentTcpPortInput)
        contentPanel.add(agentTcpPortTextFieldPanel)
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
        val buttonPanelLayout = GridLayout(1, 2, 10, 0)
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
            agentTcpPortInput.isEnabled = true
            agentTcpPortInput.isFocusable = true
            agentTcpPortInput.isEditable = true
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
            var tcpPort = -1
            try {
                tcpPort = agentTcpPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = this.getMessage(STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.tcpPort = tcpPort
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
            agentTcpPortInput.isEditable = false
            agentTcpPortInput.isFocusable = false
            agentTcpPortInput.isEnabled = false
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
        val adjustLoggerButton = JButton(this.getMessage(BUTTON_ADJUST_LOGGER_MESSAGE_KEY))
        val adjustLoggerDialog = JDialog()
        this.initializeAdjustLoggerDialog(adjustLoggerDialog)
        adjustLoggerButton.addActionListener {
            adjustLoggerDialog.isVisible = true
        }
        buttonPanel.add(startProxyBtn)
        buttonPanel.add(stopAllProxyBtn)
        buttonPanel.add(adjustLoggerButton)
        buttonPanel.border = EmptyBorder(0, 0, 10, 0)
        contentPanel.add(buttonPanel)
        val statusPanel = JPanel(CardLayout())
        statusPanel.preferredSize = Dimension(PANEL_WIDTH, 20)
        statusPanel.add(statusLabel)
        contentPanel.add(statusPanel)
        return contentPanel
    }

    private fun initializeAdjustLoggerDialog(adjustLoggerDialog: JDialog) {
        adjustLoggerDialog.isResizable = false
        val contentPanel = JPanel()
        val contentPanelLayout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.layout = contentPanelLayout
        adjustLoggerDialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                adjustLoggerDialog.isVisible = false
            }
        })
        adjustLoggerDialog.contentPane = contentPanel
        val adjustLogLevelPanel = JPanel()
        val adjustLogLevelPanelScrollPane = JScrollPane(adjustLogLevelPanel)
        adjustLogLevelPanelScrollPane.preferredSize = Dimension(PANEL_WIDTH, 200)
        adjustLogLevelPanelScrollPane.verticalScrollBarPolicy =
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        adjustLogLevelPanelScrollPane.horizontalScrollBarPolicy =
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        adjustLogLevelPanel.layout = BoxLayout(adjustLogLevelPanel, BoxLayout.Y_AXIS)
        adjustLogLevelPanel.border = EmptyBorder(5, 5, 5, 5)
        val loggerContext = LogManager.getContext(false) as LoggerContext
        loggerContext.loggers.stream().filter { it.name.startsWith("com.ppaass") }
            .sorted { o1, o2 -> o1.name.compareTo(o2.name) }.forEach {
                val loggerPanel = JPanel(FlowLayout(FlowLayout.LEFT))
                loggerPanel.border = EmptyBorder(0, 5, 5, 5)
                val selectLogLevelComboBox = JComboBox<String>()
                Level.values().forEachIndexed { index, item ->
                    selectLogLevelComboBox.addItem(item.name())
                    if (it.level == item) {
                        selectLogLevelComboBox.selectedIndex = index
                    }
                }
                loggerPanel.add(selectLogLevelComboBox)
                loggerPanel.add(JLabel(":: "+it.name.substring(it.name.lastIndexOf(".") + 1)))
                selectLogLevelComboBox.addActionListener { event ->
                    it.level = Level.getLevel(selectLogLevelComboBox.selectedItem as String)
                }
                adjustLogLevelPanel.add(loggerPanel)
            }
        contentPanel.add(adjustLogLevelPanelScrollPane)
        adjustLoggerDialog.pack()
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
