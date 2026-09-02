package com.manuskript.review;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NiReviewComment {

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_UNRESOLVED = "unresolved";

    private String id;
    private int start;
    private int end;
    private boolean zeroWidth;
    private String text;
    private String prefix;
    private String suffix;
    private String author;
    private String created;
    private String status;
    private List<NiReviewReply> replies = new ArrayList<>();

    public NiReviewComment() {
        this.id = "k-" + UUID.randomUUID().toString().substring(0, 8);
        this.text = "";
        this.prefix = "";
        this.suffix = "";
        this.status = STATUS_OPEN;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }
    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }
    public boolean isZeroWidth() { return zeroWidth; }
    public void setZeroWidth(boolean zeroWidth) { this.zeroWidth = zeroWidth; }
    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = text != null ? text : ""; }
    public String getPrefix() { return prefix == null ? "" : prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix != null ? prefix : ""; }
    public String getSuffix() { return suffix == null ? "" : suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix != null ? suffix : ""; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public String getStatus() { return status == null ? STATUS_OPEN : status; }
    public void setStatus(String status) { this.status = status; }
    public List<NiReviewReply> getReplies() {
        if (replies == null) {
            replies = new ArrayList<>();
        }
        return replies;
    }
    public void setReplies(List<NiReviewReply> replies) {
        this.replies = replies != null ? replies : new ArrayList<>();
    }

    public boolean isOpen() {
        return !STATUS_DONE.equals(getStatus());
    }
}
