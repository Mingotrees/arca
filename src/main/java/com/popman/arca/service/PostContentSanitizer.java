package com.popman.arca.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Policy for post body HTML fragments. URLs are handled by structured post links. */
public final class PostContentSanitizer {
    private static final int MAX_BODY_BYTES = 65_535; // MySQL/MariaDB TEXT, measured after escaping.
    private static final PolicyFactory TEXT_ONLY = new HtmlPolicyBuilder().toFactory();
    private static final Pattern LIST_NUMBER = Pattern.compile("-?[0-9]{1,10}");
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "h1", "h2", "h3", "b", "strong", "i", "em", "u", "s", "code",
                    "blockquote", "ol", "ul", "li")
            .allowAttributes("start").matching(LIST_NUMBER).onElements("ol")
            .allowAttributes("value").matching(LIST_NUMBER).onElements("li")
            .toFactory();

    private PostContentSanitizer() {}

    public static String sanitize(String html) {
        return html == null ? null : POLICY.sanitize(html);
    }

    public static String sanitizeForWrite(String html, boolean requireText) {
        String clean = sanitize(html);
        if (requireText && (clean == null || TEXT_ONLY.sanitize(clean).replace("&#160;", " ")
                .codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))) {
            throw new IllegalArgumentException("Post content must contain text after sanitization");
        }
        if (clean != null && clean.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Sanitized post content must not exceed 65535 UTF-8 bytes");
        }
        return clean;
    }
}
