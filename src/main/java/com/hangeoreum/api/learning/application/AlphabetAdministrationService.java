package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.domain.AlphabetLetter;
import com.hangeoreum.api.learning.infrastructure.AlphabetLetterRepository;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AlphabetAdministrationService {

    private final AlphabetLetterRepository letterRepository;
    private final MediaStorage mediaStorage;

    public record LetterRequest(Short position, String audioUrl) {
    }

    @Transactional(readOnly = true)
    public List<AlphabetLetter> letters() {
        return letterRepository.findAllByOrderByLetterGroupAscPositionAsc();
    }

    public AlphabetLetter update(UUID id, LetterRequest r) {
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

    public AlphabetLetter uploadAudio(UUID id, MultipartFile file) {
        AlphabetLetter letter = letterRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Letter"));
        letter.setAudioUrl(mediaStorage.store(file, "alphabet"));
        return letter;
    }
}
