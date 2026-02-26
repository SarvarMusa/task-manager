package org.task.taskmaganer.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Objects;

public class UpdateTaskRequest {

    @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 20, message = "Priority must not exceed 20 characters")
    private String priority;

    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status;

    private Boolean isActive;

    public UpdateTaskRequest() {}

    public UpdateTaskRequest(String title, String description, String priority, String status, Boolean isActive) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.isActive = isActive;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UpdateTaskRequest that = (UpdateTaskRequest) o;
        return Objects.equals(title, that.title) &&
               Objects.equals(description, that.description) &&
               Objects.equals(priority, that.priority) &&
               Objects.equals(status, that.status) &&
               Objects.equals(isActive, that.isActive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, priority, status, isActive);
    }
}
