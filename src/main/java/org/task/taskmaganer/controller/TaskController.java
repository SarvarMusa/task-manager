package org.task.taskmaganer.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.task.taskmaganer.dto.request.CreateTaskRequest;
import org.task.taskmaganer.dto.request.UpdateTaskRequest;
import org.task.taskmaganer.dto.response.PageResponse;
import org.task.taskmaganer.dto.response.TaskResponse;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;
import org.task.taskmaganer.service.TaskService;

import java.util.UUID;

@RestController
@RequestMapping(value = "/tasks", name = "TaskController")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
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
    public ResponseEntity<PageResponse<TaskResponse>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getAllTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<PageResponse<TaskResponse>> getAllActiveTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);

        PageResponse<TaskResponse> response = taskService.getAllActiveTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByStatus(
            @PathVariable TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/priority/{priority}")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByPriority(
            @PathVariable TaskPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByPriority(priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active/status/{status}")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByStatus(
            @PathVariable TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active/priority/{priority}")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByPriority(
            @PathVariable TaskPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByPriority(priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserId(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);

        PageResponse<TaskResponse> response = taskService.getTasksByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByUserId(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserIdAndStatus(
            @PathVariable UUID userId,
            @PathVariable TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByUserIdAndStatus(userId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/priority/{priority}")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserIdAndPriority(
            @PathVariable UUID userId,
            @PathVariable TaskPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByUserIdAndPriority(userId, priority, pageable);
        return ResponseEntity.ok(response);
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

    private Pageable createPageable(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }
}
