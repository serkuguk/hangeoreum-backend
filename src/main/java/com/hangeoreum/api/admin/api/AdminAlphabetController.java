package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.AlphabetLetter;
import com.hangeoreum.api.learning.infrastructure.AlphabetLetterRepository;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin/alphabet")
@RequiredArgsConstructor
public class AdminAlphabetController {

    private final AlphabetLetterRepository letterRepository;
    private final MediaStorage mediaStorage;

    @GetMapping
    public List<AlphabetLetter> letters() {
        return letterRepository.findAllByOrderByLetterGroupAscPositionAsc();
    }

    public record LetterRequest(Short position, String audioUrl) {
    }

    @PutMapping("/{id}")
    @Transactional
    public AlphabetLetter update(@PathVariable UUID id, @RequestBody LetterRequest r) {
        AlphabetLetter letter = letterRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Letter"));
        if (r.position() != null) {
            letter.setPosition(r.position());
        }
        if (r.audioUrl() != null) {
            letter.setAudioUrl(r.audioUrl());
        }
        return letter;
    }

    @PostMapping("/{id}/audio")
    @Transactional
    public AlphabetLetter uploadAudio(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        AlphabetLetter letter = letterRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Letter"));
        letter.setAudioUrl(mediaStorage.store(file, "alphabet"));
        return letter;
    }
}
