package com.manuskript.review;

import com.manuskript.QuoteNavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spielt Review-Tags auf den Live-Text. Schreibt niemals die ZIP-MD.
 */
public final class NiReviewMerge {

    private NiReviewMerge() {
    }

    public static NiReviewDocument ontoLive(NiReviewDocument incoming, String snapshotMd, String liveMd) {
        if (incoming == null) {
            return new NiReviewDocument();
        }
        String live = liveMd == null ? "" : liveMd;
        String snapshot = snapshotMd == null ? "" : snapshotMd;
        String liveHash = NiReviewHashes.sha256(live);
        String snapshotHash = NiReviewHashes.sha256(snapshot);
        boolean same = liveHash.equals(snapshotHash)
                || (incoming.getBaseHash() != null && liveHash.equals(incoming.getBaseHash()));

        NiReviewDocument merged = copyShallow(incoming);
        if (same) {
            merged.setBaseHash(liveHash);
            return merged;
        }

        for (NiReviewChange change : merged.getChanges()) {
            if (!change.isOpen()) {
                continue;
            }
            int[] span = locate(live, change.getOldText(), change.getPrefix(), change.getSuffix(), change.getStart());
            if (span == null && NiReviewChange.KIND_INSERT.equals(change.getKind())) {
                span = locatePoint(live, change.getPrefix(), change.getSuffix(), change.getStart());
            }
            if (span == null) {
                change.setStatus(NiReviewChange.STATUS_UNRESOLVED);
                change.setStart(-1);
                change.setEnd(-1);
            } else {
                change.setStart(span[0]);
                change.setEnd(span[1]);
            }
        }
        for (NiReviewComment comment : merged.getComments()) {
            if (!comment.isOpen()) {
                continue;
            }
            if (comment.isZeroWidth() || comment.getStart() == comment.getEnd()) {
                int[] point = locatePoint(live, comment.getPrefix(), comment.getSuffix(), comment.getStart());
                if (point == null) {
                    comment.setStatus(NiReviewComment.STATUS_UNRESOLVED);
                    comment.setStart(-1);
                    comment.setEnd(-1);
                } else {
                    comment.setStart(point[0]);
                    comment.setEnd(point[0]);
                    comment.setZeroWidth(true);
                }
                continue;
            }
            String marked = snapshotSpan(snapshot, comment.getStart(), comment.getEnd());
            int[] span = locate(live, marked, comment.getPrefix(), comment.getSuffix(), comment.getStart());
            if (span == null) {
                comment.setStatus(NiReviewComment.STATUS_UNRESOLVED);
                comment.setStart(-1);
                comment.setEnd(-1);
            } else {
                comment.setStart(span[0]);
                comment.setEnd(span[1]);
            }
        }
        merged.setBaseHash(liveHash);
        return merged;
    }

    public static String applyChange(String baseText, NiReviewChange change) {
        String base = baseText == null ? "" : baseText;
        if (change == null || change.getStart() < 0) {
            return base;
        }
        int start = Math.max(0, Math.min(base.length(), change.getStart()));
        int end = Math.max(start, Math.min(base.length(), change.getEnd()));
        String kind = change.getKind();
        if (NiReviewChange.KIND_DELETE.equals(kind)) {
            return base.substring(0, start) + base.substring(end);
        }
        if (NiReviewChange.KIND_INSERT.equals(kind)) {
            return base.substring(0, start) + change.getNewText() + base.substring(start);
        }
        return base.substring(0, start) + change.getNewText() + base.substring(end);
    }

    public static void shiftAfterApply(NiReviewDocument document, NiReviewChange applied, int delta) {
        if (document == null || applied == null || delta == 0) {
            return;
        }
        int pivot = applied.getStart();
        for (NiReviewChange change : document.getChanges()) {
            if (change == applied || change.getStart() < 0) {
                continue;
            }
            if (change.getStart() >= pivot) {
                change.setStart(change.getStart() + delta);
                change.setEnd(change.getEnd() + delta);
            }
        }
        for (NiReviewComment comment : document.getComments()) {
            if (comment.getStart() < 0) {
                continue;
            }
            if (comment.getStart() >= pivot) {
                comment.setStart(comment.getStart() + delta);
                comment.setEnd(comment.getEnd() + delta);
            }
        }
    }

    public static void shiftAfterLiveEdit(NiReviewDocument document, String oldText, String newText) {
        if (document == null || oldText == null || newText == null) {
            return;
        }
        int changeStart = 0;
        int minLen = Math.min(oldText.length(), newText.length());
        while (changeStart < minLen && oldText.charAt(changeStart) == newText.charAt(changeStart)) {
            changeStart++;
        }
        if (changeStart >= minLen && oldText.length() == newText.length()) {
            return;
        }
        int oldEnd = oldText.length() - 1;
        int newEnd = newText.length() - 1;
        while (oldEnd >= changeStart && newEnd >= changeStart
                && oldText.charAt(oldEnd) == newText.charAt(newEnd)) {
            oldEnd--;
            newEnd--;
        }
        int changeEndOld = oldEnd + 1;
        int delta = newText.length() - oldText.length();

        for (NiReviewChange change : document.openChanges()) {
            int matchEnd = change.getEnd();
            if (change.getStart() < changeEndOld && matchEnd > changeStart) {
                change.setStatus(NiReviewChange.STATUS_UNRESOLVED);
            } else if (change.getStart() >= changeEndOld) {
                change.setStart(change.getStart() + delta);
                change.setEnd(change.getEnd() + delta);
            }
        }
        for (NiReviewComment comment : document.openComments()) {
            int matchEnd = comment.getEnd();
            if (comment.getStart() < changeEndOld && matchEnd > changeStart && !comment.isZeroWidth()) {
                comment.setStatus(NiReviewComment.STATUS_UNRESOLVED);
            } else if (comment.getStart() >= changeEndOld) {
                comment.setStart(comment.getStart() + delta);
                comment.setEnd(comment.getEnd() + delta);
            }
        }
    }

    public static NiReviewDocument copyOf(NiReviewDocument incoming) {
        return copyShallow(incoming);
    }

    public static void replaceContents(NiReviewDocument target, NiReviewDocument source) {
        if (target == null || source == null) {
            return;
        }
        NiReviewDocument copy = copyOf(source);
        target.setVersion(copy.getVersion());
        target.setRoundId(copy.getRoundId());
        target.setChapterKey(copy.getChapterKey());
        target.setChapterFile(copy.getChapterFile());
        target.setBaseHash(copy.getBaseHash());
        target.setReviewer(copy.getReviewer());
        target.setChanges(copy.getChanges());
        target.setComments(copy.getComments());
    }

    private static NiReviewDocument copyShallow(NiReviewDocument incoming) {
        NiReviewDocument copy = new NiReviewDocument();
        copy.setVersion(incoming.getVersion());
        copy.setRoundId(incoming.getRoundId());
        copy.setChapterKey(incoming.getChapterKey());
        copy.setChapterFile(incoming.getChapterFile());
        copy.setBaseHash(incoming.getBaseHash());
        copy.setReviewer(incoming.getReviewer());
        for (NiReviewChange change : incoming.getChanges()) {
            copy.getChanges().add(cloneChange(change));
        }
        for (NiReviewComment comment : incoming.getComments()) {
            copy.getComments().add(cloneComment(comment));
        }
        return copy;
    }

    private static NiReviewChange cloneChange(NiReviewChange src) {
        NiReviewChange copy = new NiReviewChange();
        copy.setId(src.getId());
        copy.setKind(src.getKind());
        copy.setStart(src.getStart());
        copy.setEnd(src.getEnd());
        copy.setOldText(src.getOldText());
        copy.setNewText(src.getNewText());
        copy.setPrefix(src.getPrefix());
        copy.setSuffix(src.getSuffix());
        copy.setAuthor(src.getAuthor());
        copy.setCreated(src.getCreated());
        copy.setStatus(src.getStatus());
        return copy;
    }

    private static NiReviewComment cloneComment(NiReviewComment src) {
        NiReviewComment copy = new NiReviewComment();
        copy.setId(src.getId());
        copy.setStart(src.getStart());
        copy.setEnd(src.getEnd());
        copy.setZeroWidth(src.isZeroWidth());
        copy.setText(src.getText());
        copy.setPrefix(src.getPrefix());
        copy.setSuffix(src.getSuffix());
        copy.setAuthor(src.getAuthor());
        copy.setCreated(src.getCreated());
        copy.setStatus(src.getStatus());
        List<NiReviewReply> replies = new ArrayList<>();
        for (NiReviewReply reply : src.getReplies()) {
            replies.add(new NiReviewReply(reply.getAuthor(), reply.getCreated(), reply.getText()));
        }
        copy.setReplies(replies);
        return copy;
    }

    private static String snapshotSpan(String snapshot, int start, int end) {
        if (snapshot == null || start < 0 || end < start || end > snapshot.length()) {
            return "";
        }
        return snapshot.substring(start, end);
    }

    static int[] locate(String live, String oldText, String prefix, String suffix, int hint) {
        if (live == null) {
            return null;
        }
        String needle = oldText == null ? "" : oldText;
        if (!needle.isEmpty()) {
            if (hint >= 0 && hint + needle.length() <= live.length()
                    && live.substring(hint, hint + needle.length()).equals(needle)) {
                return new int[]{hint, hint + needle.length()};
            }
            String withContext = (prefix == null ? "" : prefix) + needle + (suffix == null ? "" : suffix);
            if (withContext.length() > needle.length()) {
                int ctx = live.indexOf(withContext);
                if (ctx >= 0) {
                    int start = ctx + (prefix == null ? 0 : prefix.length());
                    return new int[]{start, start + needle.length()};
                }
            }
            Optional<QuoteNavigation.QuoteRange> quote = QuoteNavigation.findQuoteRangeStrict(live, needle);
            if (quote.isPresent()) {
                return new int[]{quote.get().start(), quote.get().end()};
            }
            int idx = live.indexOf(needle);
            if (idx >= 0) {
                return new int[]{idx, idx + needle.length()};
            }
            return null;
        }
        return locatePoint(live, prefix, suffix, hint);
    }

    static int[] locatePoint(String live, String prefix, String suffix, int hint) {
        if (live == null) {
            return null;
        }
        String pre = prefix == null ? "" : prefix;
        String suf = suffix == null ? "" : suffix;
        if (!pre.isEmpty() && !suf.isEmpty()) {
            int idx = live.indexOf(pre + suf);
            if (idx >= 0) {
                return new int[]{idx + pre.length(), idx + pre.length()};
            }
        }
        if (!pre.isEmpty()) {
            int idx = live.indexOf(pre);
            if (idx >= 0) {
                return new int[]{idx + pre.length(), idx + pre.length()};
            }
        }
        if (hint >= 0 && hint <= live.length()) {
            return new int[]{hint, hint};
        }
        return null;
    }
}
