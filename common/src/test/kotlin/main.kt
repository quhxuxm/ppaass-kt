import com.ppaass.kt.common.message.*
import io.netty.buffer.ByteBufUtil

fun main(args: Array<String>) {
    val agentMessage = AgentMessage("securetoken", MessageEncryptionType.BASE64_AES,
            agentMessageBody(AgentMessageBodyType.CONNECT, "id") {
                targetAddress = "target-address"
                targetPort = 1
                originalData = byteArrayOf(1, 2, 3, 4, 5)
            })
    val encodeResult = MessageSerializer.encodeAgentMessage(agentMessage)
    println(ByteBufUtil.prettyHexDump(encodeResult));
    val decodeResult = MessageSerializer.decodeAgentMessage(encodeResult)
    println(decodeResult)
}