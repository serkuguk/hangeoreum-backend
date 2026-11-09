package com.hangeoreum.api.media.domain;

import com.hangeoreum.api.shared.domain.DomainException;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/** YouTube video link. Parses the string only — no network calls. */
public record YoutubeLink(String videoId) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");

    public static YoutubeLink parse(String url) {
        String id = extractId(url);
        if (id == null || !ID.matcher(id).matches()) {
            throw DomainException.validation("Это не ссылка на видео YouTube");
        }
        return new YoutubeLink(id);
    }

    public static boolean isYoutube(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && (HOSTS.contains(host.toLowerCase()) || host.equalsIgnoreCase("youtu.be"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String canonicalUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static String extractId(String url) {
        if (url == null || url.isBlank()) return null;
        String raw = url.trim();
        if (!raw.contains("://")) raw = "https://" + raw;
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme(), host = uri.getHost(), path = uri.getPath();
        if (scheme == null || !scheme.matches("(?i)https?") || host == null || path == null) return null;
        host = host.toLowerCase();
        String[] parts = path.split("/");
        if (host.equals("youtu.be")) return parts.length == 2 ? parts[1] : null;
        if (!HOSTS.contains(host)) return null;
        if (path.equals("/watch")) return queryParam(uri.getRawQuery(), "v");
        if (parts.length == 3 && (parts[1].equals("shorts") || parts[1].equals("embed"))) return parts[2];
        return null;
    }

    private static String queryParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            if (pair.startsWith(name + "=")) return pair.substring(name.length() + 1);
        }
        return null;
    }
}
