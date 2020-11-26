package com.ppaass.agent.handler.socks

import com.ppaass.kt.common.ProxyMessage
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksProxyToAgentTcpChannelHandler : SimpleChannelInboundHandler<ProxyMessage> (){
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: ProxyMessage?) {
        var proxyChannel = proxyChannelContext.channel();
        switch (proxyMessage.getBody().getBodyType()) {
            case CONNECT_SUCCESS -> {
                var connectionInfo = proxyChannel.attr(ISAConstant.CONNECTION_INFO).get();
                if (connectionInfo == null) {
                    logger.error(
                            "Fail to send proxy message to agent because of no connection information attached, proxy channel = {}",
                            proxyChannel.id().asLongText());
                    proxyChannel.close();
                    return;
                }
                var agentChannel = connectionInfo.getAgentChannel();
                agentChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                        connectionInfo.getTargetAddressType(), connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort())).addListener(agentChannelFuture -> {
                    if (agentChannelFuture.isSuccess()) {
                        agentChannel.pipeline().addLast(this.saAgentToProxyHandler);
                        return;
                    }
                    if (agentChannelFuture.cause() instanceof ClosedChannelException) {
                        logger.error(
                                "Fail to write socks command response to client because of agent channel closed already, agent channel = {}, proxy channel = {}, target address = {}, target port = {}",
                                agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                connectionInfo.getTargetHost(), connectionInfo.getTargetPort());
                        proxyChannel.close();
                        return;
                    }
                    logger.error(
                            "Fail to write socks command response to client because of exception, agent channel = {}, proxy channel = {}, target address = {}, target port = {}",
                            agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                            connectionInfo.getTargetHost(),
                            connectionInfo.getTargetPort(), agentChannelFuture.cause());
                    proxyChannel.close();
                });
            }
            case HEARTBEAT -> {
                var connectionInfo = proxyChannel.attr(ISAConstant.CONNECTION_INFO).get();
                if (connectionInfo == null) {
                    logger.error(
                            "Fail to send proxy message to agent because of no connection information attached, proxy channel = {}",
                            proxyChannel.id().asLongText());
                    proxyChannel.close();
                    return;
                }
                var agentChannel = connectionInfo.getAgentChannel();
                var originalData = proxyMessage.getBody().getOriginalData();
                var heartbeat = this.objectMapper.readValue(originalData, Heartbeat.class);
                logger.debug(
                        "Discard proxy channel heartbeat, proxy channel = {}, agent channel = {}, heartbeat id = {} , heartbeat time = {}."
                        , proxyChannel.id().asLongText(), agentChannel.id().asLongText(), heartbeat.getId(),
                        heartbeat.getUtcDateTime());
                agentChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
            case CONNECT_FAIL -> {
                var connectionInfo = proxyChannel.attr(ISAConstant.CONNECTION_INFO).get();
                if (connectionInfo == null) {
                    logger.error(
                            "Fail to send proxy message to agent because of no connection information attached, proxy channel = {}",
                            proxyChannel.id().asLongText());
                    proxyChannel.close();
                    return;
                }
                var agentChannel = connectionInfo.getAgentChannel();
                logger.error(
                        "Fail connect to proxy, agent channel = {}, target address = {}, target port = {}",
                        agentChannel.id().asLongText(), connectionInfo.getTargetHost(),
                        connectionInfo.getTargetPort());
                agentChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                        connectionInfo.getTargetAddressType()))
                        .addListener(ChannelFutureListener.CLOSE);
            }
            case OK -> {
                var connectionInfo = proxyChannel.attr(ISAConstant.CONNECTION_INFO).get();
                if (connectionInfo == null) {
                    logger.error(
                            "Fail to send proxy message to agent because of no connection information attached, proxy channel = {}",
                            proxyChannel.id().asLongText());
                    proxyChannel.close();
                    return;
                }
                var agentChannel = connectionInfo.getAgentChannel();
                logger.debug("Write proxy message to agent: \n{}\n", proxyMessage);
                agentChannel.writeAndFlush(Unpooled.wrappedBuffer(proxyMessage.getBody().getOriginalData()))
                        .addListener(agentChannelFuture -> {
                            if (agentChannelFuture.isSuccess()) {
                                return;
                            }
                            if (agentChannelFuture.cause() instanceof ClosedChannelException) {
                                logger.error(
                                        "Fail to transfer data from agent to client because of agent channel closed already, agent channel = {}, proxy channel = {}, target address = {}, target port = {}",
                                        agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                        connectionInfo.getTargetHost(), connectionInfo.getTargetPort());
                                proxyChannel.close();
                                return;
                            }
                            logger.error(
                                    "Fail to transfer data from agent to client because of exception, agent channel = {}, proxy channel = {}, target address = {}, target port = {}.",
                                    agentChannel.id().asLongText(), proxyChannel.id().asLongText(),
                                    connectionInfo.getTargetHost(), connectionInfo.getTargetPort(),
                                    agentChannelFuture.cause());
                            proxyChannel.close();
                        });
            }
            case OK_UDP -> {
                logger.debug("Receive udp message: {}", proxyMessage);
                var udpConnectionInfo = proxyChannel.attr(ISAConstant.UDP_ASSOCIATE_INFO).get();
                var recipient = new InetSocketAddress(udpConnectionInfo.getClientSenderHost(),
                        udpConnectionInfo.getClientSenderPort());
                var sender = new InetSocketAddress(ISAConstant.UDP_LOCAL_IP,
                        udpConnectionInfo.getAgentUdpPort());
                var data = proxyMessage.getBody().getOriginalData();
                var socks5UdpResponseBuf = Unpooled.buffer();
                socks5UdpResponseBuf.writeByte(0);
                socks5UdpResponseBuf.writeByte(0);
                socks5UdpResponseBuf.writeByte(0);
                var clientRecipientHost = udpConnectionInfo.getClientRecipientHost();
                if (NetUtil.isValidIpV4Address(clientRecipientHost)) {
                    socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv4.byteValue());
                    Socks5AddressEncoder.DEFAULT
                            .encodeAddress(Socks5AddressType.IPv4, clientRecipientHost,
                                    socks5UdpResponseBuf);
                } else {
                    if (NetUtil.isValidIpV6Address(clientRecipientHost)) {
                        socks5UdpResponseBuf.writeByte(Socks5AddressType.IPv6.byteValue());
                        Socks5AddressEncoder.DEFAULT
                                .encodeAddress(Socks5AddressType.IPv6, clientRecipientHost,
                                        socks5UdpResponseBuf);
                    } else {
                        socks5UdpResponseBuf.writeByte(clientRecipientHost.length());
                        Socks5AddressEncoder.DEFAULT
                                .encodeAddress(Socks5AddressType.DOMAIN,
                                        clientRecipientHost,
                                        socks5UdpResponseBuf);
                    }
                }
                socks5UdpResponseBuf.writeShort(udpConnectionInfo.getClientRecipientPort());
                socks5UdpResponseBuf.writeBytes(data);
                logger.debug("Write udp message to client: \n{}\n", ByteBufUtil.prettyHexDump(socks5UdpResponseBuf));
                var udpPackage =
                        new DatagramPacket(socks5UdpResponseBuf, recipient, sender);
                udpConnectionInfo.getAgentUdpChannel().writeAndFlush(udpPackage);
            }
        }
    }
}
