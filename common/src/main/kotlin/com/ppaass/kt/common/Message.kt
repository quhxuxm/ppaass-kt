package com.ppaass.kt.common

import java.util.*

/**
 * The message body type
 */
interface MessageBodyType {
    /**
     * Get the value of the type
     */
    fun value(): Byte
}

/**
 * The message body
 */
class MessageBody<T>(
    /**
     * Message id
     */
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    /**
     * User token
     */
    val userToken: String,
    /**
     * Target hostname
     */
    val targetHost: String,
    /**
     * Target port
     */
    val targetPort: Int,
    /**
     * Message body type
     */
    val bodyType: T,
    /**
     * The data in bytes
     */
    val data: ByteArray) where T : MessageBodyType, T : Enum<T>

/**
 * The message exchange between agent and proxy.
 */
class Message<T>(
    /**
     * The encryption token
     */
    val encryptionToken: String = UUID.randomUUID().toString().replace("-", ""),
    /**
     * The encryption type
     */
    val encryptionType: EncryptionType,
    /**
     * The message body
     */
    val body: MessageBody<T>) where T : Enum<T>, T : MessageBodyType

/**
 * The agent message body type
 */
enum class AgentMessageBodyType(private val value: Byte) : MessageBodyType {
    /**
     * Create a connection on proxy and keep alive
     */
    CONNECT_WITH_KEEP_ALIVE(0),

    /**
     * Create a connection on proxy and do not keep alive
     */
    CONNECT_WITHOUT_KEEP_ALIVE(1),

    /**
     * Sending a TCP data
     */
    TCP_DATA(2),

    /**
     * Sending a UDP data
     */
    UDP_DATA(3);

    override fun value(): Byte {
        return this.value
    }
}

enum class ProxyMessageBodyType(private val value: Byte) : MessageBodyType {
    /**
     * TCP data handled.
     */
    OK_TCP(0),

    /**
     * UDP data handled.
     */
    OK_UDP(1),

    /**
     * Heartbeat
     */
    HEARTBEAT(2),

    /**
     * Connection fail
     */
    CONNECT_FAIL(3),

    /**
     * Connection success
     */
    CONNECT_SUCCESS(4);

    override fun value(): Byte {
        return this.value
    }
}
/**
 * The type alias of agent message
 */
typealias AgentMessage = Message<AgentMessageBodyType>
/**
 * The type alias of proxy message
 */
typealias ProxyMessage = Message<ProxyMessageBodyType>
