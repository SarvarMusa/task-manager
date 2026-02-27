package org.task.taskmaganer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Görev arama ve filtreleme isteği")
public class SearchTaskRequest {

    @Schema(description = "Arama kelimesi (başlık ve açıklamada arar)", example = "proje")
    private String searchQuery;

    @Schema(description = "Görev durumu filtresi", example = "IN_PROGRESS")
    private TaskStatus status;

    @Schema(description = "Görev önceliği filtresi", example = "HIGH")
    private TaskPriority priority;

    @Schema(description = "Kullanıcı ID filtresi", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Aktiflik filtresi", example = "true")
    private Boolean isActive;

    @Schema(description = "Son tarih başlangıç filtresi (YYYY-MM-DDTHH:mm:ss)", example = "2024-01-01T00:00:00")
    private LocalDateTime dueDateFrom;

    @Schema(description = "Son tarih bitiş filtresi", example = "2024-12-31T23:59:59")
    private LocalDateTime dueDateTo;

    @Schema(description = "Oluşturulma tarihi başlangıç filtresi", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAtFrom;

    @Schema(description = "Oluşturulma tarihi bitiş filtresi", example = "2024-12-31T23:59:59")
    private LocalDateTime createdAtTo;

    public SearchTaskRequest() {}

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getDueDateFrom() {
        return dueDateFrom;
    }

    public void setDueDateFrom(LocalDateTime dueDateFrom) {
        this.dueDateFrom = dueDateFrom;
    }

    public LocalDateTime getDueDateTo() {
        return dueDateTo;
    }

    public void setDueDateTo(LocalDateTime dueDateTo) {
        this.dueDateTo = dueDateTo;
    }

    public LocalDateTime getCreatedAtFrom() {
        return createdAtFrom;
    }

    public void setCreatedAtFrom(LocalDateTime createdAtFrom) {
        this.createdAtFrom = createdAtFrom;
    }

    public LocalDateTime getCreatedAtTo() {
        return createdAtTo;
    }

    public void setCreatedAtTo(LocalDateTime createdAtTo) {
        this.createdAtTo = createdAtTo;
    }
}
