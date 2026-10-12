package com.hangeoreum.api.shared.storage;

import com.hangeoreum.api.shared.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageTest {
    @TempDir Path directory;
    MediaStorage storage = new MediaStorage();

    @BeforeEach
    void configureStorage() {
        ReflectionTestUtils.setField(storage, "storageDir", directory.toString());
    }

    @Test
    void acceptsPngAndJpegAndRemovesTrailingActiveContent() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
            bytes.write("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
            String url = storage.storeAvatar(new MockMultipartFile("file", "avatar." + format,
                    "text/html", bytes.toByteArray()));
            assertTrue(url.matches("/media/avatars/[0-9a-f-]+\\.png"));
            Path stored = directory.resolve(url.substring("/media/".length()));
            assertEquals(2, ImageIO.read(stored.toFile()).getWidth());
            assertFalse(new String(Files.readAllBytes(stored), StandardCharsets.ISO_8859_1).contains("<script>"));
        }
    }

    @Test
    void rejectsEmptyActiveForgedOversizedAndExcessiveDimensions() throws Exception {
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
        var wide = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2049, 1, BufferedImage.TYPE_INT_RGB), "png", wide);
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", new byte[0]),
                new MockMultipartFile("file", "avatar.png", "image/png", "<html>active</html>".getBytes()),
                new MockMultipartFile("file", "avatar.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("file", "avatar.html", "image/png", png.toByteArray()),
                new MockMultipartFile("file", "avatar.jpg", "image/jpeg", png.toByteArray()),
                new MockMultipartFile("file", "avatar.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "avatar.png", "image/png", wide.toByteArray())}) {
            assertThrows(ApiException.class, () -> storage.storeAvatar(file));
        }
        assertThrows(ApiException.class, () -> storage.storeAvatar(null));
        assertFalse(Files.exists(directory.resolve("avatars")));
    }

    @Test
    void deletesOnlyLocalAvatarFilesAndPreservesOtherResources() throws Exception {
        Path avatars = Files.createDirectories(directory.resolve("avatars"));
        String name = UUID.randomUUID() + ".png";
        Path avatar = Files.write(avatars.resolve(name), new byte[]{1});
        Path other = Files.write(directory.resolve(name), new byte[]{1});
        storage.deleteAvatar("/media/avatars/../" + name);
        storage.deleteAvatar("https://example.com/media/avatars/" + name);
        storage.deleteAvatar("/media/clips/" + name);
        assertTrue(Files.exists(avatar));
        assertTrue(Files.exists(other));
        storage.deleteAvatar("/media/avatars/" + name);
        assertFalse(Files.exists(avatar));
        assertTrue(Files.exists(other));
    }
}
