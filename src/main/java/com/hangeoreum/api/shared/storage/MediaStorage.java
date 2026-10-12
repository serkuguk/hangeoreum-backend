package com.hangeoreum.api.shared.storage;

import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * ponytail: local-disk storage, swap for an S3 adapter behind this same class when prod needs it.
 */
@Service
public class MediaStorage {

    private static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    private static final int MAX_AVATAR_DIMENSION = 2048;

    @Value("${app.storage.dir}")
    private String storageDir;

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_AVATAR_BYTES) {
            throw ApiException.badRequest("Avatar must be a nonempty image of at most 5 MB");
        }
        String original = file.getOriginalFilename();
        String extension = original == null || !original.contains(".") ? ""
                : original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        Path target = null;
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_AVATAR_BYTES + 1);
            if (bytes.length > MAX_AVATAR_BYTES) {
                throw ApiException.badRequest("Avatar must be at most 5 MB");
            }
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw ApiException.badRequest("Avatar must be JPEG or PNG");
                }
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!(format.equals("png") && extension.equals("png"))
                            && !(format.equals("jpeg") && (extension.equals("jpg") || extension.equals("jpeg")))) {
                        throw ApiException.badRequest("Avatar extension must match its JPEG or PNG content");
                    }
                    reader.setInput(imageInput, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_AVATAR_DIMENSION || height > MAX_AVATAR_DIMENSION) {
                        throw ApiException.badRequest("Avatar dimensions must not exceed 2048 pixels");
                    }
                    var image = reader.read(0);
                    String name = UUID.randomUUID() + ".png";
                    Path dir = Path.of(storageDir, "avatars");
                    target = dir.resolve(name);
                    Files.createDirectories(dir);
                    // Re-encoding drops user metadata and any trailing active content.
                    if (!ImageIO.write(image, "png", target.toFile())) {
                        throw new IOException("PNG encoder unavailable");
                    }
                    return "/media/avatars/" + name;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            if (target == null) {
                throw ApiException.badRequest("Avatar image cannot be decoded");
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Failed to store avatar", exception);
        }
    }

    public void deleteAvatar(String url) {
        if (url == null || !url.matches("/media/avatars/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-zA-Z0-9]+")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storageDir, "avatars", url.substring("/media/avatars/".length())));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete avatar", exception);
        }
    }

    public String store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String name = UUID.randomUUID() + ext;
        try {
            Path dir = Path.of(storageDir, subdir);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name).toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        return "/media/" + subdir + "/" + name;
    }
}
