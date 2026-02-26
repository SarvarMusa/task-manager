package org.task.taskmaganer.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.task.taskmaganer.entity.Task;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    Page<Task> findByPriority(TaskPriority priority, Pageable pageable);

    boolean existsByTitle(String title);

    Page<Task> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.isActive = true")
    Page<Task> findActiveTasksByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = :status")
    Page<Task> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.priority = :priority")
    Page<Task> findByUserIdAndPriority(@Param("userId") UUID userId, @Param("priority") TaskPriority priority, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.id = :id")
    Optional<Task> findActiveTaskById(@Param("id") UUID id);

    @Query("SELECT t FROM Task t WHERE t.isActive = true")
    Page<Task> findAllActiveTasks(Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.status = :status")
    Page<Task> findAllActiveTasksByStatus(@Param("status") TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.priority = :priority")
    Page<Task> findAllActiveTasksByPriority(@Param("priority") TaskPriority priority, Pageable pageable);
}
