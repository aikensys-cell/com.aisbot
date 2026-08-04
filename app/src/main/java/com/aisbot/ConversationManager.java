package com.aisbot;

import java.util.ArrayList;
import java.util.List;

public class ConversationManager {

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private final List<Message> history = new ArrayList<>();

    public void addUserMessage(String text) {
        history.add(new Message("user", text));
    }

    public void addAssistantMessage(String text) {
        history.add(new Message("assistant", text));
    }

    public List<Message> getHistory() {
        return new ArrayList<>(history);
    }

    public void clear() {
        history.clear();
    }
}
