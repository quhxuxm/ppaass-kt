package com.ppaass.kt.agent

import org.springframework.stereotype.Service

/**
 * The agent interface
 */
internal interface IAgent {
    /**
     * Start agent
     */
    fun start();

    /**
     * Stop agent
     */
    fun stop();

    /**
     * Init agent
     */
    fun init()
}

@Service
internal class HttpAgent(private val agentConfiguration: AgentConfiguration) : IAgent {
    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun init() {
        TODO("Not yet implemented")
    }
}

@Service
internal class SocksAgent(private val agentConfiguration: AgentConfiguration) : IAgent {
    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun init() {
        TODO("Not yet implemented")
    }
}