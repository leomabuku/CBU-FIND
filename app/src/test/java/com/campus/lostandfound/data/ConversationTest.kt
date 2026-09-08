package com.campus.lostandfound.data

import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.model.ChatMessage
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    @Test fun `message is unread only for the other participant`() {
        val conversation = Conversation(participantIds = listOf("a", "b"), updatedAt = 20, lastSenderId = "a", lastReadAt = mapOf("a" to 20, "b" to 10))
        assertFalse(conversation.isUnread("a"))
        assertTrue(conversation.isUnread("b"))
    }

    @Test fun `legacy null message timestamps decode without crashing`() {
        val message = ChatMessage.fromFirestore(
            "message-1",
            mapOf("senderId" to "a", "text" to "Hello", "createdAt" to 42L, "editedAt" to null, "deletedAt" to null)
        )
        assertEquals(42L, message.createdAt)
        assertEquals(0L, message.editedAt)
        assertEquals(0L, message.deletedAt)
    }

    @Test fun `conversation compatibility mapper accepts timestamps and null optional fields`() {
        val conversation = Conversation.fromFirestore(
            "conversation-1",
            mapOf(
                "participantIds" to listOf("a", "b"),
                "participantNames" to mapOf("a" to "Alex", "b" to "Bea"),
                "itemImageUrl" to null,
                "updatedAt" to Timestamp(Date(1_000L)),
                "lastReadAt" to mapOf("a" to null, "b" to 500L)
            )
        )
        assertEquals("conversation-1", conversation.id)
        assertEquals("", conversation.itemImageUrl)
        assertEquals(1_000L, conversation.updatedAt)
        assertEquals(0L, conversation.lastReadAt["a"])
        assertEquals(500L, conversation.lastReadAt["b"])
    }
}
