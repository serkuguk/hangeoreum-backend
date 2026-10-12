package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.application.LearningService.ReorderItem;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CourseAdministrationService {

    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final LearningTipRepository tipRepository;
    private final LessonProgressRepository progressRepository;
    private final LearningService learningService;

    // ---- courses ----

    public record CourseRequest(@NotBlank String title, String description) {
    }

    @Transactional(readOnly = true)
    public List<Course> courses() {
        return courseRepository.findAll();
    }

    public Course createCourse(@Valid CourseRequest r) {
        return courseRepository.save(Course.create(r.title(), r.description()));
    }

    public Course updateCourse(UUID id, @Valid CourseRequest r) {
        Course course = courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course"));
        course.setTitle(r.title());
        course.setDescription(r.description());
        return course;
    }

    public Course publishCourse(UUID id, PublishRequest r) {
        Course course = courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course"));
        course.setPublished(r.isPublished());
        return course;
    }

    // ---- units ----

    public record UnitRequest(UUID courseId, short position, @NotBlank String title,
                              String description, String color) {
    }

    @Transactional(readOnly = true)
    public List<Unit> units(UUID courseId) {
        return unitRepository.findByCourseIdOrderByPositionAsc(courseId);
    }

    public Unit createUnit(@Valid UnitRequest r) {
        return unitRepository.save(Unit.create(r.courseId(), r.position(), r.title(), r.description(), r.color()));
    }

    public Unit updateUnit(UUID id, @Valid UnitRequest r) {
        Unit unit = unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit"));
        unit.setTitle(r.title());
        unit.setDescription(r.description());
        unit.setColor(r.color());
        return unit;
    }

    public void deleteUnit(UUID id) {
        boolean hasProgress = lessonRepository.findByUnitIdOrderByPositionAsc(id).stream()
                .anyMatch(l -> progressRepository.existsByLessonId(l.getId()));
        if (hasProgress) {
            throw ApiException.conflict("Unit has user progress — unpublish instead of deleting");
        }
        unitRepository.deleteById(id);
    }

    public Unit publishUnit(UUID id, PublishRequest r) {
        Unit unit = unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit"));
        unit.setPublished(r.isPublished());
        return unit;
    }

    public void reorderUnits(@Valid List<@NotNull @Valid ReorderItem> items) {
        learningService.reorderUnits(items);
    }

    // ---- lessons ----

    public record LessonRequest(@NotNull UUID unitId, @PositiveOrZero short position,
                                @NotNull LessonType type, @NotBlank String title,
                                @PositiveOrZero short xpReward, boolean isFree) {
    }

    @Transactional(readOnly = true)
    public List<Lesson> lessons(UUID unitId) {
        return lessonRepository.findByUnitIdOrderByPositionAsc(unitId);
    }

    public Lesson createLesson(@Valid LessonRequest r) {
        return lessonRepository.save(Lesson.create(r.unitId(), r.position(), r.type(), r.title(),
                r.xpReward(), r.isFree()));
    }

    public Lesson updateLesson(UUID id, @Valid LessonRequest r) {
        return learningService.updateLesson(id, r.type(), r.title(), r.xpReward(), r.isFree());
    }

    public void deleteLesson(UUID id) {
        if (progressRepository.existsByLessonId(id)) {
            throw ApiException.conflict("Lesson has user progress — unpublish instead of deleting");
        }
        lessonRepository.deleteById(id);
    }

    public record PublishRequest(boolean isPublished) {
    }

    public Lesson publishLesson(UUID id, PublishRequest r) {
        Lesson lesson = learningService.lockLesson(id);
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

    public void reorderLessons(@Valid List<@NotNull @Valid ReorderItem> items) {
        learningService.reorderLessons(items);
    }
}
