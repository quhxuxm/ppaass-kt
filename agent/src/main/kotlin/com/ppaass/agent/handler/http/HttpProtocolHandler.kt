package com.ppaass.agent.handler.http

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessageBodyType
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.channels.ClosedChannelException

private class HttpProxyConnectListener constructor(
    private val agentChannel: Channel,
    private val connectionInfo: HttpConnectionInfo,
    private val agentConfiguration: AgentConfiguration,
    private val messageCarriedOnConnectTime: Any?,
    private val proxyBootstrap: Bootstrap) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes = 0

    override fun operationComplete(proxyChannelFuture: ChannelFuture) {
        if (!proxyChannelFuture.isSuccess) {
            if (failureTimes >=
                agentConfiguration.agentToProxyTcpChannelConnectRetry) {
                agentChannel.close()
                logger.error(proxyChannelFuture.cause()) {
                    "Fail to connect to proxy server because of exception, target address = ${
                        connectionInfo.targetHost
                    }, target port = ${
                        connectionInfo.targetPort
                    }."
                }
                return
            }
            failureTimes++
            logger.error {
                "Retry connect to proxy (${
                    failureTimes
                }), agent channel = ${
                    agentChannel.id().asLongText()
                }, target address = ${
                    connectionInfo.targetHost
                }, target port = ${
                    connectionInfo.targetPort
                }"
            }
            proxyBootstrap.connect(connectionInfo.targetHost,
                connectionInfo.targetPort)
                .addListener(this)
            return
        }
        val proxyChannel = proxyChannelFuture.channel()
        connectionInfo.agentChannel = agentChannel
        connectionInfo.proxyChannel = proxyChannel
        connectionInfo.userToken = agentConfiguration.userToken
        connectionInfo.httpMessageCarriedOnConnectTime = messageCarriedOnConnectTime
        proxyChannel.attr(HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo)
        agentChannel.attr(HTTP_CONNECTION_INFO).setIfAbsent(connectionInfo)
        val bodyType = if (connectionInfo.isKeepAlive) {
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
        } else {
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE
        }
        writeAgentMessageToProxy(
            bodyType = bodyType,
            userToken = connectionInfo.userToken!!,
            proxyChannel = proxyChannel,
            input = null,
            targetHost = connectionInfo.targetHost,
            targetPort = connectionInfo.targetPort) {
            if (!it.isSuccess) {
                logger.error {
                    "Fail to write http data to proxy, close the agent channel, body type = ${
                        bodyType
                    }, user token = ${
                        connectionInfo.userToken
                    }, target host = ${
                        connectionInfo.targetHost
                    }, target port = ${
                        connectionInfo.targetPort
                    }, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, agent channel = ${
                        agentChannel.id().asLongText()
                    }"
                }
                agentChannel.close()
            }
        }
    }
}

private class HttpWriteDataToProxyListener constructor(
    private val agentChannel: Channel, private val originalMessage: Any,
    private val connectionInfo: HttpConnectionInfo,
    private val agentConfiguration: AgentConfiguration) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes = 0

    override fun operationComplete(proxyChannelFuture: ChannelFuture) {
        if (proxyChannelFuture.isSuccess) {
            return
        }
        val proxyChannel = proxyChannelFuture.channel()
        if (proxyChannelFuture.cause() is ClosedChannelException) {
            logger.error(
                "Fail to write to proxy server because of proxy channel closed, target address = ${
                    connectionInfo.targetHost
                }, target port = ${
                    connectionInfo.targetPort
                }."
            )
            agentChannel.close()
            return
        }
        if (failureTimes >=
            agentConfiguration.agentToProxyTcpChannelWriteRetry) {
            logger.error(proxyChannelFuture.cause()) {
                "Fail to write to proxy server because of exception, target address = ${
                    connectionInfo.targetHost
                }, target port = ${
                    connectionInfo.targetPort
                }."
            }
            agentChannel.close()
            return
        }
        failureTimes++
        logger.error(proxyChannelFuture.cause()) {
            "Retry write to proxy (${
                failureTimes
            }), proxy channel = ${
                proxyChannel.id().asLongText()
            }, target address = ${
                connectionInfo.targetHost
            }, target port = ${
                connectionInfo.targetPort
            }."
        }
        writeAgentMessageToProxy(
            bodyType = AgentMessageBodyType.TCP_DATA,
            userToken = connectionInfo.userToken!!,
            proxyChannel = connectionInfo.proxyChannel!!,
            input = originalMessage,
            targetHost = connectionInfo.targetHost,
            targetPort = connectionInfo.targetPort,
            writeCallback = this)
    }
}

@Sharable
@Service
internal class HttpProtocolHandler(private val agentConfiguration: AgentConfiguration,
                                   private val proxyBootstrapForHttp: Bootstrap,
                                   private val proxyBootstrapForHttps: Bootstrap) :
    SimpleChannelInboundHandler<Any>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, httpProxyInput: Any) {
        val agentChannel = agentChannelContext.channel()
        (httpProxyInput as? FullHttpRequest)?.let {
            //HTTPS CONNECT request or HTTP request
            val connectionHeader = httpProxyInput.headers()[HttpHeaderNames.PROXY_CONNECTION]
                ?: httpProxyInput.headers()[HttpHeaderNames.CONNECTION]
            val connectionKeepAlive =
                HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(connectionHeader)
            if (HttpMethod.CONNECT == httpProxyInput.method()) {
                //A https request to setup the connection
                val connectionInfo = parseConnectionInfo(httpProxyInput.uri())
                connectionInfo?.let {
                    logger.debug {
                        "Incoming HTTPS method is CONNECT, request have a connection information already, agent channel = ${
                            agentChannel.id().asLongText()
                        }, connection information: ${
                            connectionInfo
                        }, http request: ${
                            httpProxyInput
                        }"
                    }
                    connectionInfo.isKeepAlive = connectionKeepAlive
                    proxyBootstrapForHttps.connect(agentConfiguration.proxyHost,
                        agentConfiguration.proxyPort!!)
                        .addListener(
                            HttpProxyConnectListener(agentChannel, connectionInfo,
                                agentConfiguration,
                                null,
                                proxyBootstrapForHttps))
                    return
                }
                logger.error {
                    "Can not parse connection information from incoming uri (1), uri = ${
                        httpProxyInput.uri()
                    }."
                }
                agentChannel.close()
                return
            }
            // A http request
            logger.debug {
                "Incoming request is a HTTP request, agent channel = ${
                    agentChannel.id().asLongText()
                }, http request: ${
                    httpProxyInput
                }"
            }
            ReferenceCountUtil.retain<Any>(httpProxyInput, 1)
            var connectionInfo = agentChannel.attr(HTTP_CONNECTION_INFO).get()
            connectionInfo?.let {
                //Http connection created already
                logger.debug {
                    "Incoming HTTP request have a connection information already, agent channel = ${
                        agentChannel.id().asLongText()
                    }, connection information: ${
                        connectionInfo
                    }, http request: ${
                        httpProxyInput
                    }"
                }
                writeAgentMessageToProxy(
                    bodyType = AgentMessageBodyType.TCP_DATA,
                    userToken = connectionInfo.userToken!!,
                    proxyChannel = connectionInfo.proxyChannel!!,
                    input = httpProxyInput,
                    targetHost = connectionInfo.targetHost,
                    targetPort = connectionInfo.targetPort,
                    writeCallback = HttpWriteDataToProxyListener(agentChannel, httpProxyInput,
                        connectionInfo,
                        agentConfiguration))
                return
            }
            //First time create http connection
            connectionInfo = parseConnectionInfo(httpProxyInput.uri())
            connectionInfo?.let {
                logger.debug {
                    "Incoming HTTP request do not a connection information, parse it from uri, agent channel = ${
                        agentChannel.id().asLongText()
                    }, connection information: ${
                        connectionInfo
                    }, http request: ${
                        httpProxyInput
                    }"
                }
                connectionInfo.isKeepAlive = connectionKeepAlive
                proxyBootstrapForHttp.connect(agentConfiguration.proxyHost,
                    agentConfiguration.proxyPort!!)
                    .addListener(
                        HttpProxyConnectListener(agentChannel, connectionInfo,
                            agentConfiguration,
                            httpProxyInput,
                            proxyBootstrapForHttp))
                return
            }
            logger.error {
                "Can not parse connection information from incoming uri (2), uri = ${
                    httpProxyInput.uri()
                }."
            }
            agentChannel.close()
            return
        }
        //A https request to send data
        val connectionInfo = agentChannel.attr(HTTP_CONNECTION_INFO).get()
        connectionInfo?.let {
            logger.debug {
                "Incoming HTTPS request have a connection information already, agent channel = ${
                    agentChannel.id().asLongText()
                }, connection information: ${
                    connectionInfo
                }, http request: ${
                    httpProxyInput
                }"
            }
            writeAgentMessageToProxy(
                bodyType = AgentMessageBodyType.TCP_DATA,
                userToken = connectionInfo.userToken!!,
                proxyChannel = connectionInfo.proxyChannel!!,
                targetHost = connectionInfo.targetHost,
                targetPort = connectionInfo.targetPort,
                input = httpProxyInput,
                writeCallback = HttpWriteDataToProxyListener(agentChannel, httpProxyInput,
                    connectionInfo,
                    agentConfiguration))
            return
        }
        logger.error(
            "Fail to send https data from agent to proxy because of no connection information attached, agent channel = {}",
            agentChannel.id().asLongText())
        agentChannel.close()
        return
    }

    override fun exceptionCaught(agentChannelContext: ChannelHandlerContext, cause: Throwable) {
        val agentChannel = agentChannelContext.channel()
        val connectionInfo = agentChannel.attr(HTTP_CONNECTION_INFO).get()
        val proxyChannel = connectionInfo?.proxyChannel
        logger.error("Exception happen on agent channel, agent channel = {}, proxy channel = {}",
            agentChannel.id().asLongText(), proxyChannel?.id()?.asLongText() ?: "", cause)
    }
}
