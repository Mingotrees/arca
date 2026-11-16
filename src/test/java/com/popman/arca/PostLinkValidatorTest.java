package com.popman.arca;

import com.popman.arca.dto.v1.post.PostLinkRequest;
import com.popman.arca.service.links.PostLinkValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PostLinkValidatorTest {
    private final PostLinkValidator validator = new PostLinkValidator();

    @ParameterizedTest
    @CsvSource({"HTTPS://ExAmPle.test:443/a/../b,https://example.test/b",
            "http://example.test:80,http://example.test/", "https://bücher.test/é,https://xn--bcher-kva.test/%C3%A9",
            "https://example.test/%7euser?q=%41#x,https://example.test/~user?q=A#x",
            "http://[2001:DB8::1]:80/,http://[2001:db8::1]/", "https://example.test./,https://example.test/"})
    void canonicalEquivalentUrlsRejectDuplicateWithoutChangingDisplay(String first, String second) {
        var link = validator.validate(List.of(new PostLinkRequest(first, " Label "))).getFirst();
        assertEquals(first, link.getUrl());
        assertEquals(second, link.getNormalizedUrl());
        assertEquals("Label", link.getLabel());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(new PostLinkRequest(first, null), new PostLinkRequest(second, null))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.test:0", "https://example.test:", "https://example.test:-1", "https://[fe80::1%25eth0]/",
            "https://bad..test/", "https://-bad.test/", "https://example.test/%00", "https://example.test/%7F", "https://example.test/%5cfoo",
            "https://example.test/\u2003bad", "https://example.test/\uD800", "https://example.test/\nX", "https://example.test@evil.test/"})
    void ambiguousOrMalformedUrlsReject(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(new PostLinkRequest(url, null))));
    }

    @Test
    void meaningfulPathQueryFragmentDifferencesRemainDistinctAndLimitsAcceptBoundary() {
        var input = List.of(new PostLinkRequest("https://example.test/A?q=1#x", ""), new PostLinkRequest("https://example.test/a?q=1#x", null),
                new PostLinkRequest("https://example.test/a?q=2#x", "x".repeat(200)), new PostLinkRequest("https://example.test/a?q=2#y", null));
        var output = validator.validate(input);
        assertEquals(4, output.size());
        assertNull(output.getFirst().getLabel());
        assertEquals(3, output.getLast().getPosition());
        String prefix = "https://example.test/";
        var boundary = java.util.stream.IntStream.range(0, 10).mapToObj(i -> new PostLinkRequest(prefix + (char) ('a' + i) + "x".repeat(2048 - prefix.length() - 1), null)).toList();
        assertEquals(10, validator.validate(boundary).size());
    }

    @Test
    void repeatedSlashesAndUnicodeSpellingsRemainDistinctResources() {
        var values = List.of("https://example.test/a//b", "https://example.test/a/b", "https://example.test/e\u0301", "https://example.test/é",
                "https://example.test/?q=e\u0301#e\u0301", "https://example.test/?q=é#é");
        var links = validator.validate(values.stream().map(url -> new PostLinkRequest(url, null)).toList());
        assertEquals(6, links.size());
        assertEquals("https://example.test/a//b", links.get(0).getNormalizedUrl());
        assertEquals("https://example.test/e%CC%81", links.get(2).getNormalizedUrl());
        assertEquals("https://example.test/?q=e%CC%81#e%CC%81", links.get(4).getNormalizedUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://faß.de/", "https://βόλος.test/", "https://a\u200Cb.test/", "https://a\u200Db.test/"})
    void idnaDeviationCharactersRejectRatherThanSilentlyMergingBrowserDestinations(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(new PostLinkRequest(url, null))));
    }
}
