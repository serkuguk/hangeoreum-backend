package com.hangeoreum.api.media.domain;

import com.hangeoreum.api.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YoutubeLinkTest {
    @Test
    void parsesSupportedFormats() {
        for (String url : new String[]{
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
                "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
                "youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ?si=abc",
                "https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "  https://YOUTU.BE/dQw4w9WgXcQ  ",
        }) {
            var link = YoutubeLink.parse(url);
            assertEquals("dQw4w9WgXcQ", link.videoId(), url);
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", link.canonicalUrl());
        }
    }

    @Test
    void rejectsEverythingElse() {
        for (String url : new String[]{
                null, "", "garbage", "https://youtube.com.evil.ru/watch?v=dQw4w9WgXcQ",
                "https://evil.ru/youtu.be/dQw4w9WgXcQ", "https://youtu.be/short",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ<script>", "https://www.youtube.com/channel/dQw4w9WgXcQ",
                "javascript:alert(1)", "ftp://youtu.be/dQw4w9WgXcQ",
        }) {
            assertThrows(DomainException.class, () -> YoutubeLink.parse(url), String.valueOf(url));
        }
    }

    @Test
    void detectsYoutubeUrls() {
        assertTrue(YoutubeLink.isYoutube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(YoutubeLink.isYoutube("/media/clips/abc.mp4"));
        assertFalse(YoutubeLink.isYoutube("https://youtube.com.evil.ru/x"));
    }
}
