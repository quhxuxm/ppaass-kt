package com.ppaass.kt.common.message

import java.util.*

/**
 * The message body encryption type
 */
enum class MessageBodyEncryptionType(val mask: String) {
    BASE64_AES_SHA1("T1"),
    AES_BASE64_SHA1("T2"),

    BASE64_AES_SHA224("T3"),
    AES_BASE64_SHA224("T4"),

    BASE64_AES_SHA256("T5"),
    AES_BASE64_SHA256("T6"),

    BASE64_AES_SHA384("T7"),
    AES_BASE64_SHA384("T8"),

    BASE64_AES_SHA512("T9"),
    AES_BASE64_SHA512("T10"),

    BASE64_PBE_SHA1("T11"),
    PBE_BASE64_SHA1("T12"),

    BASE64_PBE_SHA224("T13"),
    PBE_BASE64_SHA224("T14"),

    BASE64_PBE_SHA256("T15"),
    PBE_BASE64_SHA256("T16"),

    BASE64_PBE_SHA384("T17"),
    PBE_BASE64_SHA384("T18"),

    BASE64_PBE_SHA512("T19"),
    PBE_BASE64_SHA512("T20");

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

    override fun toString(): String {
        return "MessageBody(id='$id', targetAddress=$targetAddress, targetPort=$targetPort, originalData:\n\n${String(
                originalData ?: byteArrayOf())}\n\n)"
    }
}

/**
 * The message object.
 */
data class Message<T : MessageBody>(val secureToken: String, val messageBodyEncryptionType: MessageBodyEncryptionType,
                                    val body: T) {
    override fun toString(): String {
        return "Message(secureToken='$secureToken', messageBodyEncryptionType=$messageBodyEncryptionType, body=$body)"
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
class AgentMessageBody(val bodyType: AgentMessageBodyType, override val id: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null

    constructor(bodyType: AgentMessageBodyType, id: String, targetAddress: String?, targetPort: Int?) : this(bodyType,
            id) {
        this.targetAddress = targetAddress
        this.targetPort = targetPort
    }

    constructor(bodyType: AgentMessageBodyType, id: String, targetAddress: String?, targetPort: Int?,
                originalData: ByteArray) : this(bodyType, id, targetAddress, targetPort) {
        this.originalData = originalData
    }

    override fun toString(): String {
        return "AgentMessageBody(id='$id', bodyType=$bodyType, targetAddress=$targetAddress, targetPort=$targetPort, originalData:\n\n${String(
                originalData ?: kotlin.byteArrayOf(), Charsets.UTF_8)}\n\n)"
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

    constructor(bodyType: ProxyMessageBodyType, id: String, targetAddress: String?, targetPort: Int?) : this(bodyType,
            id) {
        this.targetAddress = targetAddress
        this.targetPort = targetPort
    }

    constructor(bodyType: ProxyMessageBodyType, id: String, targetAddress: String?, targetPort: Int?,
                originalData: ByteArray) : this(bodyType, id, targetAddress, targetPort) {
        this.originalData = originalData
    }

    override fun toString(): String {
        return "ProxyMessageBody(id='$id', bodyType=$bodyType,  targetAddress=$targetAddress, targetPort=$targetPort), originalData:\n\n${String(
                originalData ?: byteArrayOf(), Charsets.UTF_8)}\n\n"
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



