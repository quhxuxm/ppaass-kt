package com.ppaass.kt.common.protocol

import java.util.*

/**
 * The message body encryption type
 */
enum class MessageBodyEncryptionType(val mask: String) {
    AES("T1"),
    BLOWFISH("T2");

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
            // val encryptionIndex: Int = this.random.nextInt(values().size)
            // return values().get(encryptionIndex)
            return BLOWFISH
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
data class Message<T : MessageBody>(val encryptionToken: String,
                                    val messageBodyEncryptionType: MessageBodyEncryptionType,
                                    val body: T) {
    override fun toString(): String {
        return "Message(encryptionToken='$encryptionToken', messageBodyEncryptionType=$messageBodyEncryptionType, body=$body)"
    }
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
class AgentMessageBody(val bodyType: AgentMessageBodyType, override val id: String,
                       val securityToken: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null

    constructor(bodyType: AgentMessageBodyType, id: String, securityToken: String, targetAddress: String?,
                targetPort: Int?) : this(bodyType,
            id, securityToken) {
        this.targetAddress = targetAddress
        this.targetPort = targetPort
    }

    override fun toString(): String {
        return "AgentMessageBody(bodyType=$bodyType, id='$id', securityToken='$securityToken', " +
                "targetAddress=$targetAddress, targetPort=$targetPort, originalData=${originalData?.contentToString()})"
    }
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

    constructor(bodyType: ProxyMessageBodyType, id: String, targetAddress: String?,
                targetPort: Int?) : this(bodyType, id) {
        this.targetAddress = targetAddress
        this.targetPort = targetPort
    }

    override fun toString(): String {
        return "ProxyMessageBody(bodyType=$bodyType, id='$id', targetAddress=$targetAddress, " +
                "targetPort=$targetPort, originalData=${originalData?.contentToString()})"
    }
}

/**
 * The type alias of proxy message
 */
typealias ProxyMessage = Message<ProxyMessageBody>

/**
 * The type alias of agent message
 */
typealias AgentMessage = Message<AgentMessageBody>



