package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.nio.NioEventLoopGroup
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class AgentConfigure(private val agentConfiguration: AgentConfiguration) {
    @Bean
    fun proxyBootstrapIoEventLoopGroup() =
        NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.proxyBootstrapIoEventThreadNumber)

    @Bean
    fun dataTransferIoEventLoopGroup() =
        NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.dataTransferIoEventThreadNumber)

    @Bean
    fun agentMasterIoEventLoopGroup() =
        NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.masterIoEventThreadNumber)

    @Bean
    fun agentWorkerIoEventLoopGroup() =
        NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.workerIoEventThreadNumber)
}
