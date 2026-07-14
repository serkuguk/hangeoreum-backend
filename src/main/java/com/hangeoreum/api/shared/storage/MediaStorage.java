package com.hangeoreum.api.shared.storage;

import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * ponytail: local-disk storage, swap for an S3 adapter behind this same class when prod needs it.
 */
@Service
public class MediaStorage {

    @Value("${app.storage.dir}")
    private String storageDir;

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
