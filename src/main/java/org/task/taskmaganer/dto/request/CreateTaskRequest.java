package org.task.taskmaganer.dto.request;


import org.task.taskmaganer.dto.response.TaskResponse;

import java.util.Objects;

public class CreateTaskRequest {
    private String title;

    public CreateTaskRequest() {
    }

    public CreateTaskRequest(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CreateTaskRequest that = (CreateTaskRequest) o;
        return Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(title);
    }
}
