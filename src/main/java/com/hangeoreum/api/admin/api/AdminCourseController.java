package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.shared.web.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ponytail: thin admin CRUD straight over repositories; transactions live on the
 * multi-step handlers only.
 */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final LearningTipRepository tipRepository;
    private final LessonProgressRepository progressRepository;

    // ---- courses ----

    public record CourseRequest(@NotBlank String title, String description) {
    }

    @GetMapping("/courses")
    public List<Course> courses() {
        return courseRepository.findAll();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@RequestBody @Valid CourseRequest r) {
        return courseRepository.save(Course.create(r.title(), r.description()));
    }

    @PutMapping("/courses/{id}")
    @Transactional
    public Course updateCourse(@PathVariable UUID id, @RequestBody @Valid CourseRequest r) {
        Course course = courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course"));
        course.setTitle(r.title());
        course.setDescription(r.description());
        return course;
    }

    @PatchMapping("/courses/{id}/publish")
    @Transactional
    public Course publishCourse(@PathVariable UUID id, @RequestBody PublishRequest r) {
        Course course = courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course"));
        course.setPublished(r.isPublished());
        return course;
    }

    // ---- units ----

    public record UnitRequest(UUID courseId, short position, @NotBlank String title,
                              String description, String color) {
    }

    @GetMapping("/courses/{courseId}/units")
    public List<Unit> units(@PathVariable UUID courseId) {
        return unitRepository.findByCourseIdOrderByPositionAsc(courseId);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public Unit createUnit(@RequestBody @Valid UnitRequest r) {
        return unitRepository.save(Unit.create(r.courseId(), r.position(), r.title(), r.description(), r.color()));
    }

    @PutMapping("/units/{id}")
    @Transactional
    public Unit updateUnit(@PathVariable UUID id, @RequestBody @Valid UnitRequest r) {
        Unit unit = unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit"));
        unit.setTitle(r.title());
        unit.setDescription(r.description());
        unit.setColor(r.color());
        return unit;
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteUnit(@PathVariable UUID id) {
        boolean hasProgress = lessonRepository.findByUnitIdOrderByPositionAsc(id).stream()
                .anyMatch(l -> progressRepository.existsByLessonId(l.getId()));
        if (hasProgress) {
            throw ApiException.conflict("Unit has user progress — unpublish instead of deleting");
        }
        unitRepository.deleteById(id);
    }

    @PatchMapping("/units/{id}/publish")
    @Transactional
    public Unit publishUnit(@PathVariable UUID id, @RequestBody PublishRequest r) {
        Unit unit = unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit"));
        unit.setPublished(r.isPublished());
        return unit;
    }

    public record ReorderItem(UUID id, short position) {
    }

    @PostMapping("/units/reorder")
    @Transactional
    public void reorderUnits(@RequestBody List<ReorderItem> items) {
        for (ReorderItem item : items) {
            unitRepository.findById(item.id()).ifPresent(u -> u.setPosition(item.position()));
        }
    }

    // ---- lessons ----

    public record LessonRequest(UUID unitId, short position, LessonType type, @NotBlank String title,
                                short xpReward, boolean isFree) {
    }

    @GetMapping("/units/{unitId}/lessons")
    public List<Lesson> lessons(@PathVariable UUID unitId) {
        return lessonRepository.findByUnitIdOrderByPositionAsc(unitId);
    }

    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public Lesson createLesson(@RequestBody @Valid LessonRequest r) {
        return lessonRepository.save(Lesson.create(r.unitId(), r.position(), r.type(), r.title(),
                r.xpReward(), r.isFree()));
    }

    @PutMapping("/lessons/{id}")
    @Transactional
    public Lesson updateLesson(@PathVariable UUID id, @RequestBody @Valid LessonRequest r) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lesson"));
        lesson.setType(r.type());
        lesson.setTitle(r.title());
        lesson.setXpReward(r.xpReward());
        lesson.setFree(r.isFree());
        return lesson;
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable UUID id) {
        if (progressRepository.existsByLessonId(id)) {
            throw ApiException.conflict("Lesson has user progress — unpublish instead of deleting");
        }
        lessonRepository.deleteById(id);
    }

    public record PublishRequest(boolean isPublished) {
    }

    @PatchMapping("/lessons/{id}/publish")
    @Transactional
    public Lesson publishLesson(@PathVariable UUID id, @RequestBody PublishRequest r) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lesson"));
        if (r.isPublished()) {
            if (exerciseRepository.countByLessonId(id) == 0) {
                throw ApiException.conflict("Lesson needs at least one exercise to be published");
            }
            boolean tipRequired = lesson.getType() == LessonType.GRAMMAR || lesson.getType() == LessonType.LESSON;
            if (tipRequired && tipRepository.findByLessonId(id).isEmpty()) {
                throw ApiException.conflict("Lesson of type " + lesson.getType() + " needs a tip to be published");
            }
        }
        lesson.setPublished(r.isPublished());
        return lesson;
    }

    @PostMapping("/lessons/reorder")
    @Transactional
    public void reorderLessons(@RequestBody List<ReorderItem> items) {
        for (ReorderItem item : items) {
            lessonRepository.findById(item.id()).ifPresent(l -> l.setPosition(item.position()));
        }
    }
}
