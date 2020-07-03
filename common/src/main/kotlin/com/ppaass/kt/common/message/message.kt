package com.ppaass.kt.common.message

import java.util.*

/**
 * The message body encryption type
 */
enum class MessageBodyEncryptionType(val mask: String) {
    BASE64_AES("T1"), AES_BASE64("T2");

    companion object {
        private val random = Random()

        /**
         * Convert a string to MessageBodyEncryptionType
         *
         * @param mask The target string
         * @return The MessageBodyEncryptionType object
         */
        fun fromMask(mask: String): MessageBodyEncryptionType? {
            values().forEach {
                if (it.mask == mask) {
                    return it;
                }
            }
            return null
        }

        /**
         * Randomly give a MessageBodyEncryptionType object
         */
        fun random(): MessageBodyEncryptionType {
            val encryptionIndex: Int = this.random.nextInt(values().size)
            return values().get(encryptionIndex)
        }
    }
}

/**
 * The message body
 */
sealed class MessageBody {
    /**
     * Original data
     */
    abstract val originalData: ByteArray?

    /**
     * The target address related to the message
     */
    abstract var targetAddress: String?

    /**
     * The target port related to the message
     */
    abstract var targetPort: Int?

    /**
     * The message id.
     */
    abstract val id: String
}

/**
 * The message object.
 */
data class Message<T : MessageBody>(val secureToken: String, val messageBodyEncryptionType: MessageBodyEncryptionType,
                                    val body: T) {
}

/**
 * The agent message body type
 */
enum class AgentMessageBodyType {
    /**
     * A connect message
     */
    CONNECT,

    /**
     * A data message
     */
    DATA
}

/**
 * The agent message body
 */
class AgentMessageBody(val bodyType: AgentMessageBodyType, override val id: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null
}

/**
 * The proxy message body type
 */
enum class ProxyMessageBodyType {
    /**
     * OK message
     */
    OK,

    /**
     * Heartbeat message
     */
    HEARTBEAT,

    /**
     * Connect fail message
     */
    CONNECT_FAIL
}

/**
 * The proxy message body
 */
class ProxyMessageBody(val bodyType: ProxyMessageBodyType, override val id: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null
}

/**
 * The type alias of proxy message
 */
typealias ProxyMessage = Message<ProxyMessageBody>

/**
 * The type alias of agent message
 */
typealias AgentMessage = Message<AgentMessageBody>

/**
 * The factory method to create proxy message body
 *
 * @param bodyType The message body type.
 * @param id The message id.
 * @param block The DSL to initialize the body.
 */
fun proxyMessageBody(bodyType: ProxyMessageBodyType, id: String, block: ProxyMessageBody.() -> Unit): ProxyMessageBody {
    val messageBody = ProxyMessageBody(bodyType, id)
    block(messageBody)
    return messageBody
}

/**
 * The factory method to create agent message body.
 *
 * @param bodyType The message body type.
 * @param id The message id.
 * @param block The DSL to initialize the body.
 */
fun agentMessageBody(bodyType: AgentMessageBodyType, id: String, block: AgentMessageBody.() -> Unit): AgentMessageBody {
    val messageBody = AgentMessageBody(bodyType, id)
    block(messageBody)
    return messageBody
}


