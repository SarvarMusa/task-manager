package org.task.taskmaganer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;

import java.util.Objects;

@Schema(description = "Yeni görev oluşturma isteği")
public class CreateTaskRequest {

    @NotNull(message = "Title is required")
    @Schema(description = "Görev başlığı", example = "Yeni proje planı", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Görev açıklaması", example = "Proje için detaylı plan hazırlanacak")
    private String description;

    @NotNull(message = "Priority is required")
    @Schema(description = "Görev önceliği", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskPriority priority;

    @NotNull(message = "Status is required")
    @Schema(description = "Görev durumu", example = "TODO", requiredMode = Schema.RequiredMode.REQUIRED)
    private TaskStatus status;

    @NotNull(message = "User ID is required")
    @Schema(description = "Atanacak kullanıcı ID'si", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    public CreateTaskRequest() {
    }

    public CreateTaskRequest(String title, String description, TaskPriority priority, TaskStatus status, String userId) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.userId = userId;
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

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CreateTaskRequest that = (CreateTaskRequest) o;
        return Objects.equals(title, that.title) &&
                Objects.equals(description, that.description) &&
                priority == that.priority &&
                status == that.status &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, priority, status, userId);
    }
}
