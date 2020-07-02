import com.ppaass.kt.common.message.*
import io.netty.buffer.ByteBufUtil

fun main(args: Array<String>) {
    val agentMessageBody = AgentMessageBody(byteArrayOf(1, 2, 3, 4, 5), AgentMessageBodyType.CONNECT, "id", "address", 8080)
    val agentMessage: Message<AgentMessageBody> = Message("securetoekn", MessageEncryptionType.AES_BASE64, agentMessageBody)
    val encodeResult = MessageSerializer.encode(agentMessage)
    println(ByteBufUtil.prettyHexDump(encodeResult));
    val decodeResult = MessageSerializer.decode(encodeResult, AgentMessageBody::class.java)
    println(decodeResult)
}