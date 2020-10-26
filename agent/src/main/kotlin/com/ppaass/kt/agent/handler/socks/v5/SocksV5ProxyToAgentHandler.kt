package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBody
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ProxyToAgentHandler(
    private val agentConfiguration: AgentConfiguration,
    private val socksV5AgentToProxyHandler: SocksV5AgentToProxyHandler,
    private val dataTransferIoEventLoopGroup: EventLoopGroup) :
    SimpleChannelInboundHandler<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        val agentChannelId = agentChannel.id().asLongText()
        val socks5CommandRequest = proxyChannel.attr(SOCKS_V5_COMMAND_REQUEST).get()
        val agentMessageBody =
            AgentMessageBody(bodyType = AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE,
                id = agentChannelId,
                securityToken = agentConfiguration.userToken,
                targetAddress = socks5CommandRequest.dstAddr(),
                targetPort = socks5CommandRequest.dstPort())
        if (agentChannel.isOpen) {
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
        }
        if (proxyChannel.isOpen) {
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
        }
        val agentMessage = AgentMessage(
            encryptionToken = generateUid(),
            messageBodyEncryptionType = MessageBodyEncryptionType.random(),
            body = agentMessageBody)
        agentChannel.attr(PROXY_CHANNEL_CONTEXT).setIfAbsent(proxyChannelContext)
        agentChannel.attr(SOCKS_V5_COMMAND_REQUEST).setIfAbsent(socks5CommandRequest)
        agentChannel.pipeline().apply {
            val handlersToRemove = agentChannel.attr(HANDLERS_TO_REMOVE_AFTER_PROXY_ACTIVE).get()
            handlersToRemove?.forEach {
                try {
                    remove(it)
                } catch (e: NoSuchElementException) {
                    logger.debug { "The handler removed from pipeline already, handler = $it" }
                }
            }
            if (this[SocksV5AgentToProxyHandler::class.java] == null) {
                addLast(dataTransferIoEventLoopGroup, socksV5AgentToProxyHandler)
            }
        }
        val proxyChannelFuture = proxyChannel.writeAndFlush(agentMessage).sync()
        if (!proxyChannelFuture.isSuccess) {
            proxyChannelContext.close()
            agentChannel.writeAndFlush(
                DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                    socks5CommandRequest.dstAddrType()))
                .addListener(ChannelFutureListener.CLOSE)
            logger.debug(
                "Fail to send connect message from agent to proxy because of exception.",
                proxyChannelFuture.cause())
            return
        }
        logger.debug(
            "Success connect to target server: {}:{}", socks5CommandRequest.dstAddr(),
            socks5CommandRequest.dstPort())
        agentChannel.writeAndFlush(DefaultSocks5CommandResponse(
            Socks5CommandStatus.SUCCESS,
            socks5CommandRequest.dstAddrType(),
            socks5CommandRequest.dstAddr(),
            socks5CommandRequest.dstPort())).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
    }

    override fun channelInactive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        proxyChannel.attr(AGENT_CHANNEL_CONTEXT).set(null)
        proxyChannel.attr(SOCKS_V5_COMMAND_REQUEST).set(null)
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, msg: ProxyMessage) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        val originalDataBuf = Unpooled.wrappedBuffer(msg.body.originalData)
        if (!agentChannel.isActive) {
            proxyChannelContext.close()
            agentChannel.close()
            logger.debug(
                "Fail to send message from proxy to agent because of agent channel not active.")
            return
        }
        agentChannel.writeAndFlush(originalDataBuf)
    }

    override fun channelReadComplete(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        agentChannel.flush()
        proxyChannelContext.flush()
    }
}
