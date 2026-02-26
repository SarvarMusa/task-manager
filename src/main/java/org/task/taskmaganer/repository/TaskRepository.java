package org.task.taskmaganer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.task.taskmaganer.entity.Task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(String status);

    List<Task> findByPriority(String priority);

    boolean existsByTitle(String title);

    List<Task> findByUserId(UUID userId);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.isActive = true")
    List<Task> findActiveTasksByUserId(@Param("userId") UUID userId);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = :status")
    List<Task> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.priority = :priority")
    List<Task> findByUserIdAndPriority(@Param("userId") UUID userId, @Param("priority") String priority);

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.id = :id")
    Optional<Task> findActiveTaskById(@Param("id") UUID id);

    @Query("SELECT t FROM Task t WHERE t.isActive = true")
    List<Task> findAllActiveTasks();

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.status = :status")
    List<Task> findAllActiveTasksByStatus(@Param("status") String status);

    @Query("SELECT t FROM Task t WHERE t.isActive = true AND t.priority = :priority")
    List<Task> findAllActiveTasksByPriority(@Param("priority") String priority);
}
