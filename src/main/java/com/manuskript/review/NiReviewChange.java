package com.manuskript.review;

import java.util.UUID;

public class NiReviewChange {

    public static final String KIND_REPLACE = "replace";
    public static final String KIND_DELETE = "delete";
    public static final String KIND_INSERT = "insert";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_UNRESOLVED = "unresolved";

    private String id;
    private String kind;
    private int start;
    private int end;
    private String oldText;
    private String newText;
    private String prefix;
    private String suffix;
    private String author;
    private String created;
    private String status;

    public NiReviewChange() {
        this.id = "c-" + UUID.randomUUID().toString().substring(0, 8);
        this.kind = KIND_REPLACE;
        this.oldText = "";
        this.newText = "";
        this.prefix = "";
        this.suffix = "";
        this.status = STATUS_PENDING;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }
    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }
    public String getOldText() { return oldText == null ? "" : oldText; }
    public void setOldText(String oldText) { this.oldText = oldText != null ? oldText : ""; }
    public String getNewText() { return newText == null ? "" : newText; }
    public void setNewText(String newText) { this.newText = newText != null ? newText : ""; }
    public String getPrefix() { return prefix == null ? "" : prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix != null ? prefix : ""; }
    public String getSuffix() { return suffix == null ? "" : suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix != null ? suffix : ""; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public String getStatus() { return status == null ? STATUS_PENDING : status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isOpen() {
        String s = getStatus();
        return STATUS_PENDING.equals(s) || STATUS_UNRESOLVED.equals(s);
    }
}
