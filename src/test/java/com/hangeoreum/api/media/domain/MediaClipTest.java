package com.hangeoreum.api.media.domain;
import com.hangeoreum.api.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MediaClipTest {
    @Test void immersionPublicationRequiresUnitTitleVideoAndSubtitles() {
        var clip = MediaClip.create(ClipKind.IMMERSE, null, null);
        clip.setAudioUrl("audio");
        assertThrows(ApiException.class, () -> clip.publish(1));
        clip.setVideoUrl("video");
        clip.setUnitId(UUID.randomUUID());
        assertThrows(ApiException.class, () -> clip.publish(1));
        clip.setTitle("Greeting");
        assertThrows(ApiException.class, () -> clip.publish(0));
        clip.publish(1);
        assertTrue(clip.isPublished());
        clip.setTitle(" ");
        assertThrows(ApiException.class, () -> clip.publish(1));
        assertThrows(ApiException.class, () -> clip.setPosition(-1));
    }
}
