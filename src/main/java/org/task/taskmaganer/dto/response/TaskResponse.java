package org.task.taskmaganer.dto.response;

import org.task.taskmaganer.entity.Task;

import java.util.Objects;

public class TaskResponse {
    private String id;
    private String title;
    private boolean done;

    public TaskResponse(String title, boolean done, String id) {
        this.title = title;
        this.done = done;
        this.id = id;
    }

    public TaskResponse(Task task) {
        this.title = task.getTitle();
        this.done = task.isDone();
        this.id = task.getId().toString();
    }

    public TaskResponse() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TaskResponse that = (TaskResponse) o;
        return done == that.done && Objects.equals(id, that.id) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, done);
    }
}
