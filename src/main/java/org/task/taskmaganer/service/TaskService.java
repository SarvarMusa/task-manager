package org.task.taskmaganer.service;

import org.springframework.stereotype.Service;
import org.task.taskmaganer.dto.request.CreateTaskRequest;
import org.task.taskmaganer.dto.response.TaskResponse;
import org.task.taskmaganer.entity.Task;
import org.task.taskmaganer.repository.TaskRepository;

import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponse> findAll() {
        return this.taskRepository.findAll()
                .stream()
                .map(TaskResponse::new)
                .toList();
    }

    public Task save(CreateTaskRequest request) {
        return taskRepository.save(new Task(request.getTitle()));
    }

    public void delete(String id) {
        UUID taskId = UUID.fromString(id);
        taskRepository.deleteById(taskId);
    }

    public Task findById(String id) {
        UUID taskId = UUID.fromString(id);
        return taskRepository.findById(taskId).orElse(null);
    }
}
