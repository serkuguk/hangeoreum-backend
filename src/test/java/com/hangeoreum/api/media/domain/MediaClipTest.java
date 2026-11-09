package com.hangeoreum.api.media.domain;
import com.hangeoreum.api.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MediaClipTest {
    @Test void immersionPublicationRequiresUnitTitleVideoAndSubtitles() {
        var clip = MediaClip.create(ClipKind.IMMERSE, null, null);
        clip.setAudioUrl("audio");
        assertThrows(DomainException.class, () -> clip.publish(1));
        clip.setVideoUrl("video");
        clip.setUnitId(UUID.randomUUID());
        assertThrows(DomainException.class, () -> clip.publish(1));
        clip.setTitle("Greeting");
        assertThrows(DomainException.class, () -> clip.publish(0));
        clip.publish(1);
        assertTrue(clip.isPublished());
        clip.setTitle(" ");
        assertThrows(DomainException.class, () -> clip.publish(1));
        assertThrows(DomainException.class, () -> clip.setPosition(-1));
    }
}
