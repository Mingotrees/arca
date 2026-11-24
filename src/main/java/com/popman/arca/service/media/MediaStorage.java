package com.popman.arca.service.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Reject symbolic links below the configured storage root on both reads and writes. */
public final class MediaStorage {
    public Path destination(String uploadDir, String relativeDirectory, String filename) throws IOException {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        root = root.toRealPath();
        Path directory = root.resolve(relativeDirectory).normalize();
        requireContained(root, directory);
        Path current = root;
        for (Path part : root.relativize(directory)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Invalid media directory");
            Files.createDirectories(current);
        }
        Path target = directory.resolve(filename).normalize();
        requireContained(directory, target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Media destination already exists");
        return target;
    }

    public Path existing(String uploadDir, String storedPath, String relativeDirectory) throws IOException {
        Path configuredRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        Path root = configuredRoot.toRealPath();
        Path stored = Path.of(storedPath);
        Path candidate = stored.isAbsolute() ? stored.normalize() : configuredRoot.resolve(stored).normalize();
        // Stored attachment paths may be absolute; avatars are relative to upload root.
        Path relative;
        if (candidate.startsWith(configuredRoot)) relative = configuredRoot.relativize(candidate);
        else if (candidate.startsWith(root)) relative = root.relativize(candidate);
        else throw new IOException("Invalid media path");
        requireContained(root.resolve(relativeDirectory).normalize(), root.resolve(relative));
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Invalid media path");
        }
        Path real = current.toRealPath();
        requireContained(root.resolve(relativeDirectory).normalize(), real);
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(real)) {
            throw new IOException("Media not found");
        }
        return real;
    }

    private void requireContained(Path root, Path candidate) throws IOException {
        if (candidate.equals(root) || !candidate.startsWith(root)) throw new IOException("Invalid media path");
    }
}
