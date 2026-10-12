package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.identity.api.UserDto;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.hangeoreum.api.identity.application.UserAdministrationService;
import com.hangeoreum.api.identity.application.UserAdministrationService.*;
import com.hangeoreum.api.shared.web.PageResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdministrationService service;

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String search,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.users(search, page, size);
    }

    @PatchMapping("/users/{id}")
    public UserDto patchUser(@PathVariable UUID id, @RequestBody PatchUserRequest r) {
        return service.patchUser(CurrentUser.id(), id, r);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        service.deleteUser(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}")
    public UserDetail userDetail(@PathVariable UUID id) {
        return service.userDetail(id);
    }

    // ---- broadcast ----

    @PostMapping("/notifications/broadcast")
    public Map<String, Integer> broadcast(@RequestBody @Valid BroadcastRequest r) {
        return service.broadcast(r);
    }
}
