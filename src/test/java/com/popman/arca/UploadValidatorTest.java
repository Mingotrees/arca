package com.popman.arca;

import com.popman.arca.service.media.UploadValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class UploadValidatorTest {
    private final UploadValidator validator = new UploadValidator();

    @Test
    void rejectsEmptyUploadAndUnsupportedExtension() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]), false));
        var unsupported = new MockMultipartFile("file", "payload.html", "text/html", "<script>bad()</script>".getBytes());
        assertEquals(415, assertThrows(ResponseStatusException.class, () -> validator.validate(unsupported, false))
                .getStatusCode().value());
    }

    @Test
    void rejectsDeclaredMimeMismatchAndPdfAvatar() {
        var spoofed = new MockMultipartFile("file", "document.pdf", "image/png", "%PDF-1.7".getBytes());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(spoofed, false));
        var pdf = new MockMultipartFile("file", "document.pdf", "application/pdf", "%PDF-1.7".getBytes());
        assertEquals(415, assertThrows(ResponseStatusException.class, () -> validator.validate(pdf, true))
                .getStatusCode().value());
    }

    @Test
    void boundedStreamReadDoesNotTrustReportedSize() {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-1.7".getBytes(), 0, bytes, 0, 8);
        var dishonest = new MockMultipartFile("file", "large.pdf", "application/pdf", bytes) {
            @Override public long getSize() { return 1; }
        };
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> validator.validate(dishonest, false))
                .getStatusCode().value());
    }
}
