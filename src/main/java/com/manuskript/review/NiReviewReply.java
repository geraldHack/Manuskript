package com.manuskript.review;

public class NiReviewReply {
    private String author;
    private String created;
    private String text;

    public NiReviewReply() {
    }

    public NiReviewReply(String author, String created, String text) {
        this.author = author;
        this.created = created;
        this.text = text;
    }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = text; }
}
