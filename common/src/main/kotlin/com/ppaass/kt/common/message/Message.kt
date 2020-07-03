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
    abstract val originalData: ByteArray?;
}

class Message<T : MessageBody> {
    var secureToken: String? = null
    var encryptionType: MessageEncryptionType? = null
    var body: T? = null

    override fun toString(): String {
        return "Message(secureToken=$secureToken, encryptionType=$encryptionType, body=$body)"
    }
}

enum class AgentMessageBodyType {
    CONNECT, DATA
}

class AgentMessageBody : MessageBody() {
    override var originalData: ByteArray? = null
    var bodyType: AgentMessageBodyType? = null
    var id: String? = null
    var targetAddress: String? = null
    var targetPort: Int? = null

    override fun toString(): String {
        return "AgentMessageBody(originalData=${originalData?.contentToString()}, bodyType=$bodyType, id=$id, targetAddress=$targetAddress, targetPort=$targetPort)"
    }
}

enum class ProxyMessageBodyType {
    OK, HEARTBEAT, CONNECT_FAIL
}

class ProxyMessageBody : MessageBody() {
    override var originalData: ByteArray? = null
    var bodyType: ProxyMessageBodyType? = null
    var id: String? = null
    var targetAddress: String? = null
    var targetPort: Int? = null

    override fun toString(): String {
        return "ProxyMessageBody(originalData=${originalData?.contentToString()}, bodyType=$bodyType, id=$id, targetAddress=$targetAddress, targetPort=$targetPort)"
    }
}

typealias ProxyMessage = Message<ProxyMessageBody>
typealias AgentMessage = Message<AgentMessageBody>

fun proxyMessage(block: Message<ProxyMessageBody>.() -> Unit): ProxyMessage {
    val message = Message<ProxyMessageBody>()
    block(message)
    return message
}

fun proxyMessageBody(block: ProxyMessageBody.() -> Unit): ProxyMessageBody {
    val messageBody = ProxyMessageBody()
    block(messageBody)
    return messageBody
}

fun agentMessage(block: Message<AgentMessageBody>.() -> Unit): AgentMessage {
    val message = Message<AgentMessageBody>()
    block(message)
    return message
}

fun agentMessageBody(block: AgentMessageBody.() -> Unit): AgentMessageBody {
    val messageBody = AgentMessageBody()
    block(messageBody)
    return messageBody
}


