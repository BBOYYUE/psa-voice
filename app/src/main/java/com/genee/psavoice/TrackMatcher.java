package com.genee.psavoice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java track matching between a search query and QQ Music metadata.
 * Kept free of Android dependencies so it can be unit-checked with javac/java.
 *
 * A match is "strict" when the normalized title is fully covered by the query,
 * so an old track like "晴天娃娃" is never confirmed for a "晴天" request.
 * A non-strict (loose) match, e.g. artist-only, must be re-checked against a
 * metadata change before it may confirm playback.
 */
final class TrackMatcher {
    private TrackMatcher() { }

    static final class Result {
        final boolean matched;
        final boolean strict;
        private Result(boolean matched, boolean strict) { this.matched = matched; this.strict = strict; }
    }

    static Result match(String query, String title, String artist) {
        String titleNorm = normalize(title);
        String artistNorm = normalize(artist);
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) return new Result(false, false);
        StringBuilder joined = new StringBuilder();
        boolean titleHit = false;
        for (String token : tokens) {
            joined.append(token);
            boolean inTitle = !titleNorm.isEmpty() && (titleNorm.contains(token) || token.contains(titleNorm));
            boolean inArtist = !artistNorm.isEmpty() && (artistNorm.contains(token) || token.contains(artistNorm));
            if (!inTitle && !inArtist) return new Result(false, false);
            titleHit |= inTitle;
        }
        if (!titleHit || titleNorm.isEmpty()) return new Result(true, false);
        boolean strict = joined.toString().contains(titleNorm);
        return new Result(true, strict);
    }

    static String trackKey(String title, String artist) {
        return normalize(title) + " " + normalize(artist);
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}]", "");
    }

    private static List<String> tokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        for (String word : Normalizer.normalize(query.trim(), Normalizer.Form.NFKC).split("\\s+")) {
            String token = normalize(word);
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }
}
