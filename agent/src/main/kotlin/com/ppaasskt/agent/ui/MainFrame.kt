package com.ppaasskt.agent.ui

import com.ppaasskt.agent.core.api.IAgent
import org.springframework.stereotype.Service

@Service
class MainFrame( agent: IAgent) {
    private val agent: IAgent;

    init {
        this.agent = agent;
    }

    public fun start() {

    }
}