import com.ppaass.kt.common.message.*
import io.netty.buffer.ByteBufUtil

fun main(args: Array<String>) {
    val agentMessage = AgentMessage("securetoken", MessageEncryptionType.BASE64_AES,
            agentMessageBody(AgentMessageBodyType.CONNECT, "id") {
                originalData = byteArrayOf(1, 2, 3, 4, 5)
            })
    val agentMessageEncodeResult = MessageSerializer.encodeAgentMessage(agentMessage)
    println(ByteBufUtil.prettyHexDump(agentMessageEncodeResult));
    val agentMessageDecodeResult = MessageSerializer.decodeAgentMessage(agentMessageEncodeResult)
    println(agentMessageDecodeResult)
    val proxyMessage = ProxyMessage("securetoken", MessageEncryptionType.BASE64_AES,
            proxyMessageBody(ProxyMessageBodyType.HEARTBEAT, "id") {
                originalData = byteArrayOf(1, 2, 3, 4, 5)
            })
    val proxyMessageEncodeResult = MessageSerializer.encodeProxyMessage(proxyMessage)
    println(ByteBufUtil.prettyHexDump(proxyMessageEncodeResult));
    val proxyMessageDecodeResult = MessageSerializer.decodeProxyMessage(proxyMessageEncodeResult)
    println(proxyMessageDecodeResult)
}