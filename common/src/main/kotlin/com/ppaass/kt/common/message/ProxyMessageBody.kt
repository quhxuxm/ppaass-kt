package com.ppaass.kt.common.message

enum class ProxyMessageBodyType {
    OK, HEARTBEAT, CONNECT_FAIL
}

data class ProxyMessageBody(override val originalData: ByteArray?, val bodyType: ProxyMessageBodyType, val id: String, val targetAddress: String?,
                            val targetPort: Int?) : IMessageBody {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProxyMessageBody

        if (originalData != null) {
            if (other.originalData == null) return false
            if (!originalData.contentEquals(other.originalData)) return false
        } else if (other.originalData != null) return false
        if (bodyType != other.bodyType) return false
        if (id != other.id) return false
        if (targetAddress != other.targetAddress) return false
        if (targetPort != other.targetPort) return false

        return true
    }

    override fun hashCode(): Int {
        var result = originalData?.contentHashCode() ?: 0
        result = 31 * result + bodyType.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (targetAddress?.hashCode() ?: 0)
        result = 31 * result + (targetPort ?: 0)
        return result
    }
}