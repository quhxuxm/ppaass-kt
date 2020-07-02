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

sealed class IMessageBody<T : IMessageBody<T>> {
    abstract val originalData: ByteArray?;
}

data class Message<T : IMessageBody<T>>(val secureToken: String, val encryptionType: MessageEncryptionType, val body: T) {
}

enum class AgentMessageBodyType {
    CONNECT, DATA
}

data class AgentMessageBody(override val originalData: ByteArray?,
                            val bodyType: AgentMessageBodyType, val id: String, val targetAddress: String?,
                            val targetPort: Int?) : IMessageBody<AgentMessageBody>() {
}

enum class ProxyMessageBodyType {
    OK, HEARTBEAT, CONNECT_FAIL
}

data class ProxyMessageBody(override val originalData: ByteArray?, val bodyType: ProxyMessageBodyType, val id: String, val targetAddress: String?,
                            val targetPort: Int?) : IMessageBody<ProxyMessageBody>() {
}
