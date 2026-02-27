package org.task.taskmaganer.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.task.taskmaganer.entity.Task;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Task sorguları için JPA Specification builder.
 * <p>
 * Clean Code prensipleri:
 * - SRP: Her metod tek bir sorumluluk (tek bir filtre kriteri)
 * - Open/Closed: Yeni filtre eklemek için mevcut kodu değiştirmeye gerek yok
 * - DRY: Tekrar eden logic'ler ortak metodlara çıkarıldı
 * - Builder Pattern: Specification'ları birleştirme esnekliği
 */
public class TaskSpecification {

    private TaskSpecification() {
        // Utility class - instantiation engelleme
        throw new AssertionError("Utility class - do not instantiate");
    }

    // ==================== COMPOSITE SPECIFICATIONS ====================

    /**
     * Tüm filtre kriterlerini birleştiren composite specification.
     * Null olan kriterler ignore edilir.
     */
    public static Specification<Task> withFilters(TaskFilterCriteria criteria) {
        return Specification.where(
                withSearchQuery(criteria.searchQuery())
                        .and(withStatus(criteria.status()))
                        .and(withPriority(criteria.priority()))
                        .and(withUserId(criteria.userId()))
                        .and(withIsActive(criteria.isActive()))
                        .and(withDueDateBetween(criteria.dueDateFrom(), criteria.dueDateTo()))
                        .and(withCreatedAtBetween(criteria.createdAtFrom(), criteria.createdAtTo()))
        );
    }

    // ==================== BASIC SPECIFICATIONS ====================

    public static Specification<Task> withSearchQuery(String searchQuery) {
        return (root, query, cb) -> {
            if (isEmpty(searchQuery)) {
                return cb.conjunction();
            }

            String pattern = createLikePattern(searchQuery);
            Predicate titleMatch = cb.like(cb.lower(root.get(Task.Fields.title)), pattern);
            Predicate descMatch = cb.like(cb.lower(root.get(Task.Fields.description)), pattern);

            return cb.or(titleMatch, descMatch);
        };
    }

    public static Specification<Task> withStatus(TaskStatus status) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.status), status);
    }

    public static Specification<Task> withPriority(TaskPriority priority) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.priority), priority);
    }

    public static Specification<Task> withUserId(UUID userId) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.user).get("id"), userId);
    }

    public static Specification<Task> withIsActive(Boolean isActive) {
        return (root, query, cb) ->
                equalsPredicate(cb, root.get(Task.Fields.isActive), isActive);
    }

    public static Specification<Task> withActiveStatus() {
        return withIsActive(true);
    }

    // ==================== DATE RANGE SPECIFICATIONS ====================

    public static Specification<Task> withDueDateBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) ->
                dateRangePredicate(cb, root.get(Task.Fields.dueDate), from, to);
    }

    public static Specification<Task> withCreatedAtBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) ->
                dateRangePredicate(cb, root.get(Task.Fields.createdAt), from, to);
    }

    // ==================== HELPER METHODS ====================

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static String createLikePattern(String searchQuery) {
        return "%" + searchQuery.toLowerCase().trim() + "%";
    }

    private static <T> Predicate equalsPredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<T> path,
            T value) {
        return Optional.ofNullable(value)
                .map(v -> cb.equal(path, v))
                .orElse(cb.conjunction());
    }

    private static <T extends Comparable<? super T>> Predicate dateRangePredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<T> path,
            T from,
            T to) {

        List<Predicate> predicates = new ArrayList<>();

        Optional.ofNullable(from)
                .ifPresent(f -> predicates.add(cb.greaterThanOrEqualTo(path, f)));

        Optional.ofNullable(to)
                .ifPresent(t -> predicates.add(cb.lessThanOrEqualTo(path, t)));

        if (predicates.isEmpty()) {
            return cb.conjunction();
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    // ==================== RECORD FOR FILTER CRITERIA ====================

    /**
     * Tüm filtre kriterlerini bir arada tutan record.
     * Null safety ve immutability sağlar.
     */
    public record TaskFilterCriteria(
            String searchQuery,
            TaskStatus status,
            TaskPriority priority,
            UUID userId,
            Boolean isActive,
            LocalDateTime dueDateFrom,
            LocalDateTime dueDateTo,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo
    ) {
        // Compact constructor - validation
        public TaskFilterCriteria {
            // searchQuery null ise boş string yap
            if (searchQuery != null) {
                searchQuery = searchQuery.trim();
            }
        }

        // Builder pattern alternative
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String searchQuery;
            private TaskStatus status;
            private TaskPriority priority;
            private UUID userId;
            private Boolean isActive;
            private LocalDateTime dueDateFrom;
            private LocalDateTime dueDateTo;
            private LocalDateTime createdAtFrom;
            private LocalDateTime createdAtTo;

            public Builder searchQuery(String searchQuery) {
                this.searchQuery = searchQuery;
                return this;
            }

            public Builder status(TaskStatus status) {
                this.status = status;
                return this;
            }

            public Builder priority(TaskPriority priority) {
                this.priority = priority;
                return this;
            }

            public Builder userId(UUID userId) {
                this.userId = userId;
                return this;
            }

            public Builder isActive(Boolean isActive) {
                this.isActive = isActive;
                return this;
            }

            public Builder dueDateFrom(LocalDateTime dueDateFrom) {
                this.dueDateFrom = dueDateFrom;
                return this;
            }

            public Builder dueDateTo(LocalDateTime dueDateTo) {
                this.dueDateTo = dueDateTo;
                return this;
            }

            public Builder createdAtFrom(LocalDateTime createdAtFrom) {
                this.createdAtFrom = createdAtFrom;
                return this;
            }

            public Builder createdAtTo(LocalDateTime createdAtTo) {
                this.createdAtTo = createdAtTo;
                return this;
            }

            public TaskFilterCriteria build() {
                return new TaskFilterCriteria(
                        searchQuery, status, priority, userId, isActive,
                        dueDateFrom, dueDateTo, createdAtFrom, createdAtTo
                );
            }
        }
    }
}
