package com.popman.arca.service.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** Bounded content validation shared by attachments and avatars. */
public final class UploadValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 25_000_000;

    public ValidatedUpload validate(MultipartFile file, boolean avatar) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File must not be empty");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) throw new IllegalArgumentException("File name is required");
        String name = safeFilename(originalName);
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
        String type = switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> avatar ? "image/webp" : null;
            case ".pdf" -> avatar ? null : "application/pdf";
            default -> null;
        };
        if (type == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported upload type");
        String declared = file.getContentType();
        if (declared != null && !declared.isBlank() && !type.equalsIgnoreCase(declared.trim())) {
            throw new IllegalArgumentException("Declared media type does not match file extension");
        }
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        if (bytes.length == 0) throw new IllegalArgumentException("File must not be empty");
        if (type.equals("application/pdf")) {
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                    || bytes[3] != 'F' || bytes[4] != '-') {
                throw new IllegalArgumentException("File extension does not match file content");
            }
        } else {
            validateImage(bytes, type);
        }
        return new ValidatedUpload(name, extension, type, bytes);
    }

    private void validateImage(byte[] bytes, String type) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image content");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(type.equals("image/jpeg") && (format.equals("jpeg") || format.equals("jpg"))
                        || type.equals("image/png") && format.equals("png")
                        || type.equals("image/webp") && format.equals("webp"))) {
                    throw new IllegalArgumentException("File extension does not match image content");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image must not exceed 25000000 pixels");
                }
                var image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Invalid image content");
                image.flush();
            } finally { reader.dispose(); }
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid image content");
        }
    }

    public static String safeFilename(String original) {
        if (original == null) return "download";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("^\\.+", "");
        if (name.isBlank()) return "download";
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String suffix = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
            name = name.substring(0, 200 - suffix.length()) + suffix;
        }
        return name;
    }

    public static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded content exceeds the size limit");
    }

    public record ValidatedUpload(String fileName, String extension, String contentType, byte[] bytes) {}
}
