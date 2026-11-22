package net.venipa.stashling.core.whisparr

import net.venipa.stashling.core.whisparr.SignalR.Message
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Frames shaped as Whisparr eros sends them (MessageHub "receiveMessage" + ResourceChangeMessage, camelCase STJson).
class SignalRTest {
    private val rs = SignalR.RS

    @Test
    fun handshakeRequest() {
        assertEquals("{\"protocol\":\"json\",\"version\":1}\u001e", SignalR.HANDSHAKE)
        assertEquals("{\"type\":6}\u001e", SignalR.PING)
    }

    @Test
    fun handshakeReplies() {
        assertEquals(Message.HandshakeOk, SignalR.parse("{}"))
        assertEquals(Message.HandshakeError, SignalR.parse("{\"error\":\"Requested protocol 'json' is not available.\"}"))
        assertEquals(Message.Ping, SignalR.parse("{\"type\":6}"))
        assertEquals(Message.Close, SignalR.parse("{\"type\":7,\"error\":\"Server timeout\",\"allowReconnect\":true}"))
        assertEquals(Message.Other, SignalR.parse("not json"))
    }

    @Test
    fun splitKeepsPartialMessage() {
        val (msgs, rest) = SignalR.split("{}$rs{\"type\":6}$rs{\"type\":1,\"tar")
        assertEquals(listOf("{}", "{\"type\":6}"), msgs)
        assertEquals("{\"type\":1,\"tar", rest)
        val (more, end) = SignalR.split(rest + "get\":\"x\"}$rs")
        assertEquals(listOf("{\"type\":1,\"target\":\"x\"}"), more)
        assertEquals("", end)
    }

    @Test
    fun events() {
        val frames =
            "{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"body\":{\"version\":\"3.0.0.1234\"},\"name\":\"version\"}]}$rs" +
                "{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"body\":{\"action\":\"sync\"},\"name\":\"queue\"}]}$rs" +
                "{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"body\":{\"resource\":{\"id\":42,\"title\":\"A\",\"foreignId\":\"f\",\"stashId\":\"abc\",\"hasFile\":true},\"action\":\"updated\"},\"name\":\"movie\"}]}$rs" +
                "{\"type\":1,\"target\":\"receiveMessage\",\"arguments\":[{\"body\":{\"resource\":{\"id\":7,\"name\":\"MoviesSearch\",\"status\":\"completed\",\"body\":{\"movieIds\":[42]}},\"action\":\"updated\"},\"name\":\"command\"}]}$rs"
        val (msgs, rest) = SignalR.split(frames)
        assertEquals("", rest)
        val events = msgs.map { (SignalR.parse(it) as Message.Event).event }
        assertEquals(
            listOf(
                WhisparrEvent("version"),
                WhisparrEvent("queue", "sync"),
                WhisparrEvent("movie", "updated", id = 42, stashId = "abc"),
                WhisparrEvent("command", "updated", id = 7, commandName = "MoviesSearch", status = "completed"),
            ),
            events,
        )
    }
}
