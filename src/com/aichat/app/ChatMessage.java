package com.aichat.app;

/** 一条对话消息。 */
public class ChatMessage {

    public final String role;
    public final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
