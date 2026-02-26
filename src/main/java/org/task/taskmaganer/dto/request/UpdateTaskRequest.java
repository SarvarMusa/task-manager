package org.task.taskmaganer.dto.request;

import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;

import java.util.Objects;

public class UpdateTaskRequest {

    private String title;

    private String description;

    private TaskPriority priority;

    private TaskStatus status;

    private Boolean isActive;

    public UpdateTaskRequest() {}

    public UpdateTaskRequest(String title, String description, TaskPriority priority, TaskStatus status, Boolean isActive) {
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
               priority == that.priority &&
               status == that.status &&
               Objects.equals(isActive, that.isActive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, priority, status, isActive);
    }
}
