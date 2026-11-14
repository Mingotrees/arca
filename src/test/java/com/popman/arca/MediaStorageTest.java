package com.popman.arca;

import com.popman.arca.service.media.MediaStorage;
import com.popman.arca.service.media.UploadValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class MediaStorageTest {
    @TempDir Path temporary;

    @Test
    void configuredRootSymlinkCanReadCanonicalDestinationItJustWrote() throws Exception {
        Path physical = Files.createDirectory(temporary.resolve("physical"));
        Path alias = Files.createSymbolicLink(temporary.resolve("uploads"), physical);
        MediaStorage storage = new MediaStorage();
        Path target = storage.destination(alias.toString(), "posts/10", "test.pdf");
        Files.writeString(target, "%PDF-1.7");
        assertEquals(target, storage.existing(alias.toString(), target.toString(), "posts"));
    }

    @Test
    void webpAvatarStillDecodesWithBoundedValidation() throws Exception {
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var result = new UploadValidator().validate(new MockMultipartFile("file", "avatar.webp", "image/webp", webp), true);
        assertEquals("image/webp", result.contentType());
        assertArrayEquals(webp, result.bytes());
    }
}
