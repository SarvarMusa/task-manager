package org.task.taskmaganer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.task.taskmaganer.entity.Task;

import java.time.LocalDateTime;
import java.util.Objects;

@Schema(description = "Görev yanıt modeli")
public class TaskResponse {

    @Schema(description = "Görev ID'si", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Görev başlığı", example = "Yeni proje planı")
    private String title;

    @Schema(description = "Görev açıklaması", example = "Proje için detaylı plan hazırlanacak")
    private String description;

    @Schema(description = "Görev önceliği", example = "HIGH")
    private String priority;

    @Schema(description = "Görev durumu", example = "TODO")
    private String status;

    @Schema(description = "Atanan kullanıcı ID'si", example = "550e8400-e29b-41d4-a716-446655440001")
    private String userId;

    @Schema(description = "Atanan kullanıcı adı", example = "johndoe")
    private String username;

    @Schema(description = "Görev aktiflik durumu", example = "true")
    private Boolean isActive;

    @Schema(description = "Oluşturulma tarihi", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Güncellenme tarihi", example = "2024-01-15T14:45:00")
    private LocalDateTime updatedAt;

    public TaskResponse() {}

    public TaskResponse(String id, String title, String description, String priority, String status,
                        String userId, String username, Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.userId = userId;
        this.username = username;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public TaskResponse(Task task) {
        this.id = task.getId().toString();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.priority = task.getPriority().name();
        this.status = task.getStatus().name();
        this.userId = task.getUser().getId().toString();
        this.username = task.getUser().getUsername();
        this.isActive = task.getIsActive();
        this.createdAt = task.getCreatedAt();
        this.updatedAt = task.getUpdatedAt();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TaskResponse that = (TaskResponse) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(title, that.title) &&
               Objects.equals(description, that.description) &&
               Objects.equals(priority, that.priority) &&
               Objects.equals(status, that.status) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(username, that.username) &&
               Objects.equals(isActive, that.isActive) &&
               Objects.equals(createdAt, that.createdAt) &&
               Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, description, priority, status, userId, username, isActive, createdAt, updatedAt);
    }
}
