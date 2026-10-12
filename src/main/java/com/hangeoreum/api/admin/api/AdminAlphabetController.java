package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.AlphabetLetter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.hangeoreum.api.learning.application.AlphabetAdministrationService;
import com.hangeoreum.api.learning.application.AlphabetAdministrationService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin/alphabet")
@RequiredArgsConstructor
public class AdminAlphabetController {

    private final AlphabetAdministrationService service;

    @GetMapping
    public List<AlphabetLetter> letters() {
        return service.letters();
    }

    @PutMapping("/{id}")
    public AlphabetLetter update(@PathVariable UUID id, @RequestBody LetterRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/{id}/audio")
    public AlphabetLetter uploadAudio(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.uploadAudio(id, file);
    }
}
