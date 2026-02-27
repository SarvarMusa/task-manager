package org.task.taskmaganer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.task.taskmaganer.dto.request.CreateTaskRequest;
import org.task.taskmaganer.dto.request.SearchTaskRequest;
import org.task.taskmaganer.dto.request.UpdateTaskRequest;
import org.task.taskmaganer.dto.response.PageResponse;
import org.task.taskmaganer.dto.response.TaskResponse;
import org.task.taskmaganer.entity.Task;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;
import org.task.taskmaganer.entity.User;
import org.task.taskmaganer.exception.ResourceNotFoundException;
import org.task.taskmaganer.repository.TaskRepository;
import org.task.taskmaganer.repository.UserRepository;
import org.task.taskmaganer.specification.TaskSpecification;

import java.util.UUID;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public TaskResponse createTask(CreateTaskRequest request) {
        UUID userId = UUID.fromString(request.getUserId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setStatus(request.getStatus());
        task.setUser(user);
        task.setIsActive(true);

        Task savedTask = taskRepository.save(task);
        return new TaskResponse(savedTask);
    }

    public TaskResponse getTaskById(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return new TaskResponse(task);
    }

    public PageResponse<TaskResponse> getAllTasks(Pageable pageable) {
        Page<Task> taskPage = taskRepository.findAll(pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getAllActiveTasks(Pageable pageable) {
        Page<Task> taskPage = taskRepository.findAllActiveTasks(pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getTasksByStatus(TaskStatus status, Pageable pageable) {
        Page<Task> taskPage = taskRepository.findByStatus(status, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getTasksByPriority(TaskPriority priority, Pageable pageable) {
        Page<Task> taskPage = taskRepository.findByPriority(priority, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getActiveTasksByStatus(TaskStatus status, Pageable pageable) {
        Page<Task> taskPage = taskRepository.findAllActiveTasksByStatus(status, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getActiveTasksByPriority(TaskPriority priority, Pageable pageable) {
        Page<Task> taskPage = taskRepository.findAllActiveTasksByPriority(priority, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getTasksByUserId(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        Page<Task> taskPage = taskRepository.findByUserId(userId, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getActiveTasksByUserId(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        Page<Task> taskPage = taskRepository.findActiveTasksByUserId(userId, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getTasksByUserIdAndStatus(UUID userId, TaskStatus status, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        Page<Task> taskPage = taskRepository.findByUserIdAndStatus(userId, status, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> getTasksByUserIdAndPriority(UUID userId, TaskPriority priority, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        Page<Task> taskPage = taskRepository.findByUserIdAndPriority(userId, priority, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> searchTasks(SearchTaskRequest request, Pageable pageable) {
        Specification<Task> spec = TaskSpecification.withFilters(
                request.getSearchQuery(),
                request.getStatus(),
                request.getPriority(),
                request.getUserId(),
                request.getIsActive(),
                request.getDueDateFrom(),
                request.getDueDateTo(),
                request.getCreatedAtFrom(),
                request.getCreatedAtTo()
        );

        Page<Task> taskPage = taskRepository.findAll(spec, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public PageResponse<TaskResponse> searchTasksByQuery(String searchQuery, Pageable pageable) {
        Specification<Task> spec = TaskSpecification.withSearchQuery(searchQuery);
        Page<Task> taskPage = taskRepository.findAll(spec, pageable);
        Page<TaskResponse> responsePage = taskPage.map(TaskResponse::new);
        return new PageResponse<>(responsePage);
    }

    public TaskResponse updateTask(UUID id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }

        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        if (request.getIsActive() != null) {
            task.setIsActive(request.getIsActive());
        }

        Task updatedTask = taskRepository.save(task);
        return new TaskResponse(updatedTask);
    }

    public void deleteTask(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        task.setIsActive(false);
        taskRepository.save(task);
    }

    public void hardDeleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task not found with id: " + id);
        }
        taskRepository.deleteById(id);
    }

    public boolean existsByTitle(String title) {
        return taskRepository.existsByTitle(title);
    }
}
