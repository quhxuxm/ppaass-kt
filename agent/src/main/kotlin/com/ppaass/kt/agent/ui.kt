package com.ppaass.kt.agent

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.awt.*
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder

@Service
internal class MainFrame(private val applicationContext: ApplicationContext, private val messageSource: MessageSource,
                         private val agentConfiguration: AgentConfiguration) : JFrame() {
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

    var agent: Agent? = null

    private fun initialize() {
        val contentPanel = initializeContent()
        this.contentPane = contentPanel
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        this.iconImage = Toolkit.getDefaultToolkit()
                .getImage(MainFrame::class.java.classLoader.getResource(LOGO_BLACK))
        addWindowStateListener { e: WindowEvent ->
            if (e.newState == Frame.ICONIFIED || e.newState == 7) {
                this@MainFrame.isVisible = false
            }
        }
        addWindowListener(object : WindowListener {
            override fun windowDeiconified(e: WindowEvent?) {
            }

            override fun windowClosing(e: WindowEvent?) {
                this@MainFrame.agent?.stop()
            }

            override fun windowClosed(e: WindowEvent?) {
                System.exit(0)
            }

            override fun windowActivated(e: WindowEvent?) {
            }

            override fun windowDeactivated(e: WindowEvent?) {
            }

            override fun windowOpened(e: WindowEvent?) {
            }

            override fun windowIconified(e: WindowEvent?) {
            }
        })
        if (SystemTray.isSupported()) {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit()
                    .getImage(MainFrame::class.java.classLoader.getResource(LOGO_WHITE))
            val trayIcon = TrayIcon(image,
                    getMessage(SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY))
            trayIcon.isImageAutoSize = true
            trayIcon.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    when (e.button) {
                        MouseEvent.BUTTON1 -> {
                            this@MainFrame.isVisible = !this@MainFrame.isShowing
                            this@MainFrame.extendedState = NORMAL
                            toFront()
                        }
                        MouseEvent.BUTTON2 -> {
                            System.exit(0)
                        }
                        else -> {
                            //Do nothing
                        }
                    }
                }
            })
            tray.add(trayIcon)
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
        val tokenLabel = JLabel(getMessage(TOKEN_LABEL_MESSAGE_KEY))
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
        val agentPortLabel = JLabel(getMessage(AGENT_PORT_LABEL_MESSAGE_KEY))
        agentPortLabelPanel.add(agentPortLabel)
        contentPanel.add(agentPortLabelPanel)
        val agentPortTextFieldPanel = JPanel()
        agentPortTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        agentPortTextFieldPanel.layout = BoxLayout(agentPortTextFieldPanel, BoxLayout.Y_AXIS)
        agentPortTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val agentPortInput = JTextField()
        agentPortInput.text = this.agentConfiguration.port?.toString() ?: ""
        agentPortInput.disabledTextColor = Color(200, 200, 200)
        agentPortTextFieldPanel.add(agentPortInput)
        contentPanel.add(agentPortTextFieldPanel)
        val proxyAddressLabelPanel = JPanel()
        proxyAddressLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        proxyAddressLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        proxyAddressLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val proxyAddressLabel = JLabel(getMessage(PROXY_ADDRESS_LABEL_MESSAGE_KEY))
        proxyAddressLabelPanel.add(proxyAddressLabel)
        contentPanel.add(proxyAddressLabelPanel)
        val proxyAddressTextFieldPanel = JPanel()
        proxyAddressTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        proxyAddressTextFieldPanel.layout = BoxLayout(proxyAddressTextFieldPanel, BoxLayout.Y_AXIS)
        proxyAddressTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val proxyAddressInput = JTextField()
        proxyAddressInput.text = this.agentConfiguration.proxyAddress
        proxyAddressInput.disabledTextColor = Color(200, 200, 200)
        proxyAddressTextFieldPanel.add(proxyAddressInput)
        contentPanel.add(proxyAddressTextFieldPanel)
        val proxyPortLabelPanel = JPanel()
        proxyPortLabelPanel.preferredSize = Dimension(PANEL_WIDTH, 30)
        proxyPortLabelPanel.layout = FlowLayout(FlowLayout.LEFT)
        proxyPortLabelPanel.border = EmptyBorder(5, 0, 5, 0)
        val proxyPortLabel = JLabel(getMessage(PROXY_PORT_LABEL_MESSAGE_KEY))
        proxyPortLabelPanel.add(proxyPortLabel)
        contentPanel.add(proxyPortLabelPanel)
        val proxyPortTextFieldPanel = JPanel()
        proxyPortTextFieldPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        proxyPortTextFieldPanel.layout = BoxLayout(proxyPortTextFieldPanel, BoxLayout.Y_AXIS)
        proxyPortTextFieldPanel.border = EmptyBorder(5, 0, 10, 0)
        val proxyPortInput = JTextField()
        proxyPortInput.text = this.agentConfiguration.proxyPort?.toString() ?: ""
        proxyPortInput.disabledTextColor = Color(200, 200, 200)
        proxyPortTextFieldPanel.add(proxyPortInput)
        contentPanel.add(proxyPortTextFieldPanel)
        val buttonPanelLayout = GridLayout(1, 3, 10, 0)
        val buttonPanel = JPanel(buttonPanelLayout)
        buttonPanel.preferredSize = Dimension(PANEL_WIDTH, 50)
        val statusLabel = JLabel(getMessage(STATUS_LABEL_DEFAULT_MESSAGE_KEY))
        val stopAllProxyBtn = JButton(getMessage(BUTTON_STOP_PROXY_MESSAGE_KEY))
        val startHttpProxyBtn = JButton(
                getMessage(BUTTON_START_HTTP_PROXY_MESSAGE_KEY))
        val startSocks5ProxyBtn = JButton(
                getMessage(BUTTON_START_SOCKS5_PROXY_MESSAGE_KEY))
        stopAllProxyBtn.isEnabled = false

        stopAllProxyBtn.addActionListener { e: ActionEvent? ->
            val currentAgent = this.agent
            if (currentAgent == null) {
                logger.error("No agent to stop.")
                return@addActionListener
            }
            currentAgent.stop()
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
            startHttpProxyBtn.isEnabled = true
            startSocks5ProxyBtn.isEnabled = true
            stopAllProxyBtn.isEnabled = false
        }
        startHttpProxyBtn.addActionListener { e: ActionEvent ->
            if (!preVerifyToken(tokenInput)) {
                statusLabel.text = getMessage(STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.userToken = tokenInput.text
            var port: Int? = null
            try {
                port = agentPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = getMessage(STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.port = port
            var proxyPort: Int? = null
            try {
                proxyPort = proxyPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = getMessage(STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.proxyPort = proxyPort
            this.agentConfiguration.proxyAddress = proxyAddressInput.text
            try {
                val httpAgent = this.applicationContext.getBean(HttpAgent::class.java)
                httpAgent.stop()
                httpAgent.start()
                this.agent = httpAgent
            } catch (e: Exception) {
                statusLabel.text = getMessage(STATUS_AGENT_START_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            statusLabel.text = getMessage(STATUS_HTTP_PROXY_IS_RUNNING_MESSAGE_KEY)
            tokenInput.isEditable = false
            tokenInput.isFocusable = false
            tokenInput.isEnabled = false
            agentPortInput.isEditable = false
            agentPortInput.isFocusable = false
            agentPortInput.isEnabled = false
            proxyAddressInput.isEnabled = false
            proxyAddressInput.isFocusable = false
            proxyAddressInput.isEditable = false
            proxyPortInput.isEnabled = false
            proxyPortInput.isFocusable = false
            proxyPortInput.isEditable = false
            stopAllProxyBtn.isEnabled = true
            startHttpProxyBtn.isEnabled = false
            startSocks5ProxyBtn.isEnabled = false
            this.agentConfiguration.save()
        }
        startSocks5ProxyBtn.addActionListener { e: ActionEvent ->
            if (!preVerifyToken(tokenInput)) {
                statusLabel.text = getMessage(STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.userToken = tokenInput.text
            var port: Int? = null
            try {
                port = agentPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = getMessage(STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.port = port
            var proxyPort: Int? = null
            try {
                proxyPort = proxyPortInput.text.toInt()
            } catch (exception: Exception) {
                statusLabel.text = getMessage(STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            this.agentConfiguration.proxyPort = proxyPort
            this.agentConfiguration.proxyAddress = proxyAddressInput.text
            try {
                val socksAgent = this.applicationContext.getBean(SocksAgent::class.java)
                socksAgent.stop()
                socksAgent.start()
                this.agent = socksAgent
            } catch (e: Exception) {
                statusLabel.text = getMessage(STATUS_AGENT_START_FAIL_MESSAGE_KEY)
                return@addActionListener
            }
            statusLabel.text = getMessage(STATUS_SOCKS5_PROXY_IS_RUNNING_MESSAGE_KEY)
            tokenInput.isEditable = false
            tokenInput.isFocusable = false
            tokenInput.isEnabled = false
            agentPortInput.isEditable = false
            agentPortInput.isFocusable = false
            agentPortInput.isEnabled = false
            proxyAddressInput.isEnabled = false
            proxyAddressInput.isFocusable = false
            proxyAddressInput.isEditable = false
            proxyPortInput.isEnabled = false
            proxyPortInput.isFocusable = false
            proxyPortInput.isEditable = false
            stopAllProxyBtn.isEnabled = true
            startHttpProxyBtn.isEnabled = false
            startSocks5ProxyBtn.isEnabled = false
            this.agentConfiguration.save()
        }
        buttonPanel.add(startHttpProxyBtn)
        buttonPanel.add(startSocks5ProxyBtn)
        buttonPanel.add(stopAllProxyBtn)
        buttonPanel.border = EmptyBorder(0, 0, 10, 0)
        contentPanel.add(buttonPanel)
        val statusPanel = JPanel(CardLayout())
        statusPanel.preferredSize = Dimension(PANEL_WIDTH, 20)
        statusPanel.add(statusLabel)
        contentPanel.add(statusPanel)
        return contentPanel
    }

    private fun getMessage(statusTokenValidationFailMessageKey: String): String? {
        var locale = Locale.getDefault()
        if (this.agentConfiguration.staticAgentConfiguration.defaultLocale != null) {
            locale = this.agentConfiguration.staticAgentConfiguration.defaultLocale
        }
        return this.messageSource
                .getMessage(statusTokenValidationFailMessageKey, null, locale)
    }

    fun start() {
        initialize()
        this.isVisible = true
    }

    fun stop() {
        this.isVisible = false
        this.agent?.stop()
    }
}