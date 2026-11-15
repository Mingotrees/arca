package com.popman.arca.service.links;

import com.popman.arca.dto.v1.post.PostLinkRequest;
import com.popman.arca.entity.PostLink;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Pure validation/canonicalization. Never resolves a hostname or contacts a destination. */
public final class PostLinkValidator {
    public List<PostLink> validate(List<PostLinkRequest> input) {
        if (input == null) return List.of();
        if (input.size() > 10) throw invalid();
        var result = new ArrayList<PostLink>();
        var seen = new HashSet<String>();
        for (var item : input) {
            if (item == null || item.url() == null || item.url().length() > 2048) throw invalid();
            String url = item.url().strip();
            if (url.isEmpty()) throw invalid();
            String canonical = canonicalize(url);
            if (canonical.length() > 8192 || !seen.add(canonical)) throw invalid();
            String label = item.label();
            if (label != null) {
                if (label.length() > 200 || label.codePoints().anyMatch(Character::isISOControl) || hasUnpairedSurrogate(label)) throw invalid();
                label = label.strip();
                if (label.isEmpty()) label = null;
            }
            result.add(new PostLink(url, canonical, hash(canonical), label, result.size()));
        }
        return result;
    }

    private String canonicalize(String url) {
        if (hasUnpairedSurrogate(url) || url.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                || Character.isISOControl(c) || c == '\\')) throw invalid();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.isOpaque()) throw invalid();
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isEmpty() || authority.contains("@") || authority.contains("%")) throw invalid();
            String host;
            String portText;
            if (authority.startsWith("[")) {
                int closing = authority.indexOf(']');
                if (closing < 0 || uri.getHost() == null) throw invalid();
                host = authority.substring(0, closing + 1).toLowerCase(Locale.ROOT);
                portText = authority.substring(closing + 1);
            } else {
                int colon = authority.indexOf(':');
                String rawHost = colon < 0 ? authority : authority.substring(0, colon);
                portText = colon < 0 ? "" : authority.substring(colon);
                // Java IDN uses IDNA2003. Reject deviation characters rather than aliasing
                // destinations that non-transitional browser IDNA processing keeps distinct.
                if (rawHost.codePoints().anyMatch(c -> c == 0x00df || c == 0x1e9e || c == 0x03c2 || c == 0x200c || c == 0x200d)) throw invalid();
                host = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
                if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
                if (host.isEmpty() || host.length() > 253) throw invalid();
                for (String part : host.split("\\.", -1)) if (part.isEmpty() || part.length() > 63) throw invalid();
            }
            String portSuffix = "";
            if (!portText.isEmpty()) {
                if (!portText.matches(":[0-9]{1,5}")) throw invalid();
                int port = Integer.parseInt(portText.substring(1));
                if (port < 1 || port > 65535) throw invalid();
                if (!(scheme.equals("http") && port == 80) && !(scheme.equals("https") && port == 443)) portSuffix = ":" + port;
            }
            String path = escapes(uri.getRawPath());
            if (path == null || path.isEmpty()) path = "/";
            String canonical = scheme + "://" + host + portSuffix + removeDotSegments(path);
            if (uri.getRawQuery() != null) canonical += "?" + escapes(uri.getRawQuery());
            if (uri.getRawFragment() != null) canonical += "#" + escapes(uri.getRawFragment());
            return canonical;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private String escapes(String raw) {
        if (raw == null) return null;
        var normalized = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c > 127) {
                int codePoint = raw.codePointAt(i);
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (byte value : bytes) normalized.append('%').append(HexFormat.of().withUpperCase().toHexDigits(value));
                i += Character.charCount(codePoint) - 1;
                continue;
            }
            if (c != '%') { normalized.append(c); continue; }
            // URI parsing already checks escape length and hexadecimal syntax.
            int value = Integer.parseInt(raw.substring(i + 1, i + 3), 16);
            if (value <= 31 || value == 127 || value == 92) throw invalid();
            if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9')
                    || "-._~".indexOf(value) >= 0) normalized.append((char) value);
            else normalized.append('%').append(raw.substring(i + 1, i + 3).toUpperCase(Locale.ROOT));
            i += 2;
        }
        return normalized.toString();
    }

    /** RFC 3986 section 5.2.4; unlike URI.normalize(), preserves empty segments. */
    private String removeDotSegments(String input) {
        var output = new StringBuilder();
        while (!input.isEmpty()) {
            if (input.startsWith("../")) input = input.substring(3);
            else if (input.startsWith("./")) input = input.substring(2);
            else if (input.startsWith("/./")) input = input.substring(2);
            else if (input.equals("/.")) input = "/";
            else if (input.startsWith("/../") || input.equals("/..")) {
                input = input.equals("/..") ? "/" : input.substring(3);
                int slash = output.lastIndexOf("/");
                output.setLength(Math.max(0, slash));
            } else if (input.equals(".") || input.equals("..")) input = "";
            else {
                int next = input.indexOf('/', input.startsWith("/") ? 1 : 0);
                if (next < 0) next = input.length();
                output.append(input, 0, next);
                input = input.substring(next);
            }
        }
        return output.toString();
    }

    private boolean hasUnpairedSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return true;
            } else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid post links: use at most 10 unique HTTP(S) URLs (2048 characters), optional labels (200 characters), and valid URL syntax without credentials or controls");
    }
}
