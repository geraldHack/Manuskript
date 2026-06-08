package com.manuskript.agent;

/**
 * Ein Turn in einem Multi-Turn-Chat (User oder Assistant).
 */
public record ChatTurn(String role, String content) {

    public static ChatTurn user(String content) {
        return new ChatTurn("user", content);
    }

    public static ChatTurn assistant(String content) {
        return new ChatTurn("assistant", content);
    }
}
