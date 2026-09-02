package com.manuskript.review;

import java.util.ArrayList;
import java.util.List;

public class NiReviewDocument {

    private int version = 1;
    private String roundId;
    private String chapterKey;
    private String chapterFile;
    private String baseHash;
    private String reviewer;
    private List<NiReviewChange> changes = new ArrayList<>();
    private List<NiReviewComment> comments = new ArrayList<>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public String getChapterKey() { return chapterKey; }
    public void setChapterKey(String chapterKey) { this.chapterKey = chapterKey; }
    public String getChapterFile() { return chapterFile; }
    public void setChapterFile(String chapterFile) { this.chapterFile = chapterFile; }
    public String getBaseHash() { return baseHash; }
    public void setBaseHash(String baseHash) { this.baseHash = baseHash; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public List<NiReviewChange> getChanges() {
        if (changes == null) {
            changes = new ArrayList<>();
        }
        return changes;
    }
    public void setChanges(List<NiReviewChange> changes) {
        this.changes = changes != null ? changes : new ArrayList<>();
    }
    public List<NiReviewComment> getComments() {
        if (comments == null) {
            comments = new ArrayList<>();
        }
        return comments;
    }
    public void setComments(List<NiReviewComment> comments) {
        this.comments = comments != null ? comments : new ArrayList<>();
    }

    public List<NiReviewChange> openChanges() {
        List<NiReviewChange> open = new ArrayList<>();
        for (NiReviewChange change : getChanges()) {
            if (change != null && change.isOpen()) {
                open.add(change);
            }
        }
        return open;
    }

    public List<NiReviewComment> openComments() {
        List<NiReviewComment> open = new ArrayList<>();
        for (NiReviewComment comment : getComments()) {
            if (comment != null && comment.isOpen()) {
                open.add(comment);
            }
        }
        return open;
    }

    public boolean hasOpenItems() {
        return !openChanges().isEmpty() || !openComments().isEmpty();
    }
}
