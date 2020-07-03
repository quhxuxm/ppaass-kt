package com.ppaass.kt.common.message

import com.ppaass.kt.common.exception.PpaassException

enum class MessageEncryptionType(val mask: String) {
    BASE64_AES("T1"), AES_BASE64("T2");

    companion object {
        fun fromMask(mask: String): MessageEncryptionType {
            values().forEach {
                if (it.mask == mask) {
                    return it;
                }
            }
            throw PpaassException()
        }
    }
}

sealed class MessageBody {
    abstract val originalData: ByteArray?
    abstract var targetAddress: String?
    abstract var targetPort: Int?
    abstract val id: String
}

class Message<T : MessageBody>(val secureToken: String, val messageEncryptionType: MessageEncryptionType, val body: T) {
    override fun toString(): String {
        return "Message(secureToken=$secureToken, messageEncryptionType=$messageEncryptionType, body=$body)"
    }
}

enum class AgentMessageBodyType {
    CONNECT, DATA
}

class AgentMessageBody(val bodyType: AgentMessageBodyType, override val id: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null

    override fun toString(): String {
        return "AgentMessageBody(originalData=${originalData?.contentToString()}," +
                " bodyType=$bodyType, id=$id, targetAddress=$targetAddress, targetPort=$targetPort)"
    }
}

enum class ProxyMessageBodyType {
    OK, HEARTBEAT, CONNECT_FAIL
}

class ProxyMessageBody(val bodyType: ProxyMessageBodyType, override val id: String) : MessageBody() {
    override var originalData: ByteArray? = null
    override var targetAddress: String? = null
    override var targetPort: Int? = null

    override fun toString(): String {
        return "ProxyMessageBody(originalData=${originalData?.contentToString()}, " +
                "bodyType=$bodyType, id=$id, targetAddress=$targetAddress, targetPort=$targetPort)"
    }
}

typealias ProxyMessage = Message<ProxyMessageBody>
typealias AgentMessage = Message<AgentMessageBody>

fun proxyMessageBody(bodyType: ProxyMessageBodyType, id: String, block: ProxyMessageBody.() -> Unit): ProxyMessageBody {
    val messageBody = ProxyMessageBody(bodyType, id)
    block(messageBody)
    return messageBody
}

fun agentMessageBody(bodyType: AgentMessageBodyType, id: String, block: AgentMessageBody.() -> Unit): AgentMessageBody {
    val messageBody = AgentMessageBody(bodyType, id)
    block(messageBody)
    return messageBody
}


