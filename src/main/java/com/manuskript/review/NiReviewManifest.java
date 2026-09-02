package com.manuskript.review;

import java.util.ArrayList;
import java.util.List;

public class NiReviewManifest {

    public static final String STATUS_REQUESTED = "requested";
    public static final String STATUS_RETURNED = "returned";

    private int version = 1;
    private String roundId;
    private String author;
    private String created;
    private String status = STATUS_REQUESTED;
    private String projectName = "";
    private List<ChapterRef> chapters = new ArrayList<>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProjectName() { return projectName == null ? "" : projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName == null ? "" : projectName; }
    public List<ChapterRef> getChapters() {
        if (chapters == null) {
            chapters = new ArrayList<>();
        }
        return chapters;
    }
    public void setChapters(List<ChapterRef> chapters) {
        this.chapters = chapters != null ? chapters : new ArrayList<>();
    }

    public static class ChapterRef {
        private String chapterKey;
        private String mdFile;
        private String reviewFile;
        private String docxHint;
        private String baseHash;

        public String getChapterKey() { return chapterKey; }
        public void setChapterKey(String chapterKey) { this.chapterKey = chapterKey; }
        public String getMdFile() { return mdFile; }
        public void setMdFile(String mdFile) { this.mdFile = mdFile; }
        public String getReviewFile() { return reviewFile; }
        public void setReviewFile(String reviewFile) { this.reviewFile = reviewFile; }
        public String getDocxHint() { return docxHint; }
        public void setDocxHint(String docxHint) { this.docxHint = docxHint; }
        public String getBaseHash() { return baseHash; }
        public void setBaseHash(String baseHash) { this.baseHash = baseHash; }
    }
}
