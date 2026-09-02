package com.manuskript.review;

import java.util.LinkedHashMap;
import java.util.Map;

public class NiReviewProjectStatus {

    public static final String STATE_OUT = "out_for_review";
    public static final String STATE_RETURNED = "returned";

    private int version = 1;
    private Map<String, Entry> chapters = new LinkedHashMap<>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Map<String, Entry> getChapters() {
        if (chapters == null) {
            chapters = new LinkedHashMap<>();
        }
        return chapters;
    }
    public void setChapters(Map<String, Entry> chapters) {
        this.chapters = chapters != null ? chapters : new LinkedHashMap<>();
    }

    public static class Entry {
        private String roundId;
        private String baseHash;
        private String state;
        private String sentAt;

        public String getRoundId() { return roundId; }
        public void setRoundId(String roundId) { this.roundId = roundId; }
        public String getBaseHash() { return baseHash; }
        public void setBaseHash(String baseHash) { this.baseHash = baseHash; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getSentAt() { return sentAt; }
        public void setSentAt(String sentAt) { this.sentAt = sentAt; }
    }
}
