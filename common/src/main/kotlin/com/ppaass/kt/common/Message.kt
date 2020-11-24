package com.ppaass.kt.common

/**
 * The message body type
 */
interface MessageBodyType {
    /**
     * Get the value of the type
     */
    fun value(): Byte
}

class MessageBody<T>(
    val id: String, val userToken: String, val targetAddress: String,
    val targetPort: Int, val bodyType: T,
    val data: ByteArray) where T : MessageBodyType, T : Enum<T>

class Message<T>(val encryptionToken: String, val encryptionType: EncryptionType,
                 val body: MessageBody<T>) where T : Enum<T>, T : MessageBodyType

enum class AgentMessageBodyType(private val value: Byte) : MessageBodyType {
    CONNECT_WITH_KEEP_ALIVE(0),
    CONNECT_WITHOUT_KEEP_ALIVE(1),
    TCP_DATA(2),
    UDP_DATA(3);

    override fun value(): Byte {
        return this.value
    }
}

enum class ProxyMessageBodyType(private val value: Byte) : MessageBodyType {
    OK_TCP(0),
    OK_UDP(1),
    HEARTBEAT(2),
    CONNECT_FAIL(3),
    CONNECT_SUCCESS(4);

    override fun value(): Byte {
        return this.value
    }
}
typealias AgentMessage = Message<AgentMessageBodyType>
typealias ProxyMessage = Message<ProxyMessageBodyType>
