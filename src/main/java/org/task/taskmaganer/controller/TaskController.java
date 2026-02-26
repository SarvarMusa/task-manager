package org.task.taskmaganer.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.task.taskmaganer.dto.request.CreateTaskRequest;
import org.task.taskmaganer.dto.request.UpdateTaskRequest;
import org.task.taskmaganer.dto.response.TaskResponse;
import org.task.taskmaganer.service.TaskService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/tasks", name = "TaskController")
public class TaskController {

    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/")
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    public ResponseEntity<List<TaskResponse>> getAllTasks() {
        List<TaskResponse> responses = taskService.getAllTasks();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/active")
    public ResponseEntity<List<TaskResponse>> getAllActiveTasks() {
        List<TaskResponse> responses = taskService.getAllActiveTasks();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(@PathVariable String status) {
        List<TaskResponse> responses = taskService.getTasksByStatus(status);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<TaskResponse>> getTasksByPriority(@PathVariable String priority) {
        List<TaskResponse> responses = taskService.getTasksByPriority(priority);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/active/status/{status}")
    public ResponseEntity<List<TaskResponse>> getActiveTasksByStatus(@PathVariable String status) {
        List<TaskResponse> responses = taskService.getActiveTasksByStatus(status);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/active/priority/{priority}")
    public ResponseEntity<List<TaskResponse>> getActiveTasksByPriority(@PathVariable String priority) {
        List<TaskResponse> responses = taskService.getActiveTasksByPriority(priority);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TaskResponse>> getTasksByUserId(@PathVariable UUID userId) {
        List<TaskResponse> responses = taskService.getTasksByUserId(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<TaskResponse>> getActiveTasksByUserId(@PathVariable UUID userId) {
        List<TaskResponse> responses = taskService.getActiveTasksByUserId(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<List<TaskResponse>> getTasksByUserIdAndStatus(@PathVariable UUID userId, @PathVariable String status) {
        List<TaskResponse> responses = taskService.getTasksByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/{userId}/priority/{priority}")
    public ResponseEntity<List<TaskResponse>> getTasksByUserIdAndPriority(@PathVariable UUID userId, @PathVariable String priority) {
        List<TaskResponse> responses = taskService.getTasksByUserIdAndPriority(userId, priority);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse response = taskService.updateTask(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteTask(@PathVariable UUID id) {
        taskService.hardDeleteTask(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/exists/title/{title}")
    public ResponseEntity<Boolean> existsByTitle(@PathVariable String title) {
        boolean exists = taskService.existsByTitle(title);
        return ResponseEntity.ok(exists);
    }
}
