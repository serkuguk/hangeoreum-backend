package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.hangeoreum.api.learning.application.LearningService.ReorderItem;
import com.hangeoreum.api.learning.application.CourseAdministrationService;
import com.hangeoreum.api.learning.application.CourseAdministrationService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseAdministrationService service;

    @GetMapping("/courses")
    public List<Course> courses() {
        return service.courses();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@RequestBody @Valid CourseRequest r) {
        return service.createCourse(r);
    }

    @PutMapping("/courses/{id}")
    public Course updateCourse(@PathVariable UUID id, @RequestBody @Valid CourseRequest r) {
        return service.updateCourse(id, r);
    }

    @PatchMapping("/courses/{id}/publish")
    public Course publishCourse(@PathVariable UUID id, @RequestBody PublishRequest r) {
        return service.publishCourse(id, r);
    }

    // ---- units ----

    @GetMapping("/courses/{courseId}/units")
    public List<Unit> units(@PathVariable UUID courseId) {
        return service.units(courseId);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public Unit createUnit(@RequestBody @Valid UnitRequest r) {
        return service.createUnit(r);
    }

    @PutMapping("/units/{id}")
    public Unit updateUnit(@PathVariable UUID id, @RequestBody @Valid UnitRequest r) {
        return service.updateUnit(id, r);
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnit(@PathVariable UUID id) {
        service.deleteUnit(id);
    }

    @PatchMapping("/units/{id}/publish")
    public Unit publishUnit(@PathVariable UUID id, @RequestBody PublishRequest r) {
        return service.publishUnit(id, r);
    }

    @PostMapping("/units/reorder")
    public void reorderUnits(@RequestBody @Valid List<@NotNull @Valid ReorderItem> items) {
        service.reorderUnits(items);
    }

    // ---- lessons ----

    @GetMapping("/units/{unitId}/lessons")
    public List<Lesson> lessons(@PathVariable UUID unitId) {
        return service.lessons(unitId);
    }

    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public Lesson createLesson(@RequestBody @Valid LessonRequest r) {
        return service.createLesson(r);
    }

    @PutMapping("/lessons/{id}")
    public Lesson updateLesson(@PathVariable UUID id, @RequestBody @Valid LessonRequest r) {
        return service.updateLesson(id, r);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable UUID id) {
        service.deleteLesson(id);
    }

    @PatchMapping("/lessons/{id}/publish")
    public Lesson publishLesson(@PathVariable UUID id, @RequestBody PublishRequest r) {
        return service.publishLesson(id, r);
    }

    @PostMapping("/lessons/reorder")
    public void reorderLessons(@RequestBody @Valid List<@NotNull @Valid ReorderItem> items) {
        service.reorderLessons(items);
    }
}
