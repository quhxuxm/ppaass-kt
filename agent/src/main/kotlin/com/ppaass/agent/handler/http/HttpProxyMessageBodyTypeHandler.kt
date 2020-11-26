package com.ppaass.agent.handler.http

import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.Heartbeat
import com.ppaass.kt.common.JSON_OBJECT_MAPPER
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBodyType
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.channels.ClosedChannelException

@Sharable
@Service
internal class HttpProxyMessageBodyTypeHandler : SimpleChannelInboundHandler<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext,
                              proxyMessage: ProxyMessage) {
        val proxyChannel = proxyChannelContext.channel()
        val connectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        if (connectionInfo == null) {
            logger.error {
                "Close proxy channel because of no connection information attached, proxy channel = ${
                    proxyChannel.id().asLongText()
                }"
            }
            proxyChannel.close()
            return
        }
        val agentChannel = connectionInfo.agentChannel!!
        if (ProxyMessageBodyType.HEARTBEAT == proxyMessage.body.bodyType) {
            val originalData = proxyMessage.body.data
            val heartbeat = JSON_OBJECT_MAPPER.readValue(originalData, Heartbeat::class.java)
            logger.debug {
                "Discard proxy channel heartbeat, proxy channel = ${
                    proxyChannel.id().asLongText()
                }, agent channel = ${
                    agentChannel.id().asLongText()
                }, heartbeat id = ${
                    heartbeat.id
                }, heartbeat time = ${
                    heartbeat.utcDateTime
                }."
            }
            return
        }
        if (ProxyMessageBodyType.CONNECT_SUCCESS == proxyMessage.body.bodyType) {
            if (connectionInfo.isHttps) {
                //HTTPS
                val okResponse =
                    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                agentChannel.writeAndFlush(okResponse)
                    .addListener(ChannelFutureListener { agentChannelFuture ->
                        if (agentChannelFuture.isSuccess) {
                            val agentChannelPipeline = agentChannel.pipeline()
                            if (agentChannelPipeline.get(
                                    HttpServerCodec::class.java.name) != null) {
                                agentChannelPipeline.remove(HttpServerCodec::class.java.name)
                            }
                            if (agentChannelPipeline.get(
                                    HttpObjectAggregator::class.java.name) != null) {
                                agentChannelPipeline.remove(HttpObjectAggregator::class.java.name)
                            }
                            return@ChannelFutureListener
                        }
                        if (agentChannelFuture.cause() is ClosedChannelException) {
                            logger.error {
                                "Fail to transfer data from agent to client because of agent channel closed already, agent channel = ${
                                    agentChannel.id().asLongText()
                                }, proxy channel = ${
                                    proxyChannel.id().asLongText()
                                }, target address = ${
                                    connectionInfo.targetHost
                                }, target port = ${
                                    connectionInfo.targetPort
                                }"
                            }
                            proxyChannel.close()
                            return@ChannelFutureListener
                        }
                        logger.error(agentChannelFuture.cause()) {
                            "Fail to transfer data from agent to client because of exception, agent channel = ${
                                agentChannel.id().asLongText()
                            }, proxy channel = ${
                                proxyChannel.id().asLongText()
                            }, target address = ${
                                connectionInfo.targetHost
                            }, target port = ${
                                connectionInfo.targetPort
                            }."
                        }
                        proxyChannel.close()
                    })
                return
            }
            //HTTP
            writeAgentMessageToProxy(
                bodyType = AgentMessageBodyType.TCP_DATA,
                userToken = connectionInfo.userToken!!,
                proxyChannel = proxyChannel,
                input = connectionInfo.httpMessageCarriedOnConnectTime,
                connectionInfo.targetHost,
                connectionInfo.targetPort
            ) { proxyChannelFuture ->
                if (proxyChannelFuture.isSuccess) {
                    return@writeAgentMessageToProxy
                }
                if (proxyChannelFuture.cause() is ClosedChannelException) {
                    logger.error {
                        "Fail write agent original message to proxy because of proxy channel closed already, agent channel = ${
                            agentChannel.id().asLongText()
                        }, proxy channel = ${
                            proxyChannel.id().asLongText()
                        },target address = ${
                            connectionInfo.targetHost
                        }, target port = ${
                            connectionInfo.targetPort
                        }, target connection type = ${
                            AgentMessageBodyType.TCP_DATA
                        }"
                    }
                    agentChannel.close()
                    return@writeAgentMessageToProxy
                }
                logger.error(proxyChannelFuture.cause()) {
                    "Fail write agent original message to proxy because of exception, agent channel = ${
                        agentChannel.id().asLongText()
                    }, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target address = ${
                        connectionInfo.targetHost
                    }, target port = ${
                        connectionInfo.targetPort
                    }, target connection type = ${
                        AgentMessageBodyType.TCP_DATA
                    }"
                }
                agentChannel.close()
            }
            return
        }
        proxyChannelContext.fireChannelRead(proxyMessage)
    }

    override fun exceptionCaught(proxyChannelContext: ChannelHandlerContext, cause: Throwable) {
        val proxyChannel = proxyChannelContext.channel()
        val connectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        val agentChannel = connectionInfo?.agentChannel
        logger.error(cause) {
            "Exception happen on proxy channel, agent channel = ${
                agentChannel?.id()?.asLongText()
            }, proxy channel = ${
                proxyChannel.id().asLongText()
            }"
        }
    }
}
