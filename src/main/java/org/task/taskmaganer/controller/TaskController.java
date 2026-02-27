package org.task.taskmaganer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.task.taskmaganer.dto.request.SearchTaskRequest;
import org.task.taskmaganer.dto.request.UpdateTaskRequest;
import org.task.taskmaganer.dto.response.PageResponse;
import org.task.taskmaganer.dto.response.TaskResponse;
import org.task.taskmaganer.entity.TaskPriority;
import org.task.taskmaganer.entity.TaskStatus;
import org.task.taskmaganer.service.TaskService;

import java.util.UUID;

@RestController
@RequestMapping(value = "/tasks", name = "TaskController")
@Tag(name = "Task Management", description = "Görev yönetimi işlemleri için API endpointleri")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/")
    @Operation(summary = "Yeni görev oluştur", description = "Yeni bir görev oluşturur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Görev başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Görevi ID'ye göre getir", description = "Belirli bir ID'ye sahip görevi getirir")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Görev bulundu",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "404", description = "Görev bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<TaskResponse> getTaskById(
            @Parameter(description = "Görev ID'si", required = true) @PathVariable UUID id) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    @Operation(summary = "Tüm görevleri getir", description = "Tüm görevleri sayfalı olarak getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getAllTasks(
            @Parameter(description = "Sayfa numarası (0'dan başlar)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama (alan,yön formatında, örn: createdAt,desc)") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getAllTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Aktif görevleri getir", description = "Silinmemiş tüm aktif görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Aktif görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getAllActiveTasks(
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "5") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);

        PageResponse<TaskResponse> response = taskService.getAllActiveTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Duruma göre görevleri getir", description = "Belirli bir duruma sahip görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByStatus(
            @Parameter(description = "Görev durumu", required = true) @PathVariable TaskStatus status,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/priority/{priority}")
    @Operation(summary = "Önceliğe göre görevleri getir", description = "Belirli bir önceliğe sahip görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByPriority(
            @Parameter(description = "Görev önceliği", required = true) @PathVariable TaskPriority priority,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByPriority(priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active/status/{status}")
    @Operation(summary = "Duruma göre aktif görevleri getir", description = "Belirli bir duruma sahip aktif görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByStatus(
            @Parameter(description = "Görev durumu", required = true) @PathVariable TaskStatus status,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active/priority/{priority}")
    @Operation(summary = "Önceliğe göre aktif görevleri getir", description = "Belirli bir önceliğe sahip aktif görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByPriority(
            @Parameter(description = "Görev önceliği", required = true) @PathVariable TaskPriority priority,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByPriority(priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Kullanıcının görevlerini getir", description = "Belirli bir kullanıcıya ait tüm görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserId(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID userId,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);

        PageResponse<TaskResponse> response = taskService.getTasksByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/active")
    @Operation(summary = "Kullanıcının aktif görevlerini getir", description = "Belirli bir kullanıcıya ait aktif görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getActiveTasksByUserId(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID userId,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getActiveTasksByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/status/{status}")
    @Operation(summary = "Kullanıcının durumuna göre görevlerini getir", description = "Belirli bir kullanıcıya ait, belirli durumdaki görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserIdAndStatus(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID userId,
            @Parameter(description = "Görev durumu", required = true) @PathVariable TaskStatus status,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByUserIdAndStatus(userId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/priority/{priority}")
    @Operation(summary = "Kullanıcının önceliğine göre görevlerini getir", description = "Belirli bir kullanıcıya ait, belirli öncelikteki görevleri getirir")
    @ApiResponse(responseCode = "200", description = "Görevler başarıyla getirildi")
    public ResponseEntity<PageResponse<TaskResponse>> getTasksByUserIdAndPriority(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID userId,
            @Parameter(description = "Görev önceliği", required = true) @PathVariable TaskPriority priority,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.getTasksByUserIdAndPriority(userId, priority, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Görevi güncelle", description = "Mevcut bir görevi günceller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Görev başarıyla güncellendi"),
            @ApiResponse(responseCode = "404", description = "Görev bulunamadı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek")
    })
    public ResponseEntity<TaskResponse> updateTask(
            @Parameter(description = "Görev ID'si", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse response = taskService.updateTask(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Görevi sil (soft delete)", description = "Görevi soft delete yapar (deleted_at alanını doldurur)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Görev başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "Görev bulunamadı")
    })
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "Görev ID'si", required = true) @PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/hard")
    @Operation(summary = "Görevi kalıcı sil", description = "Görevi veritabanından tamamen siler")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Görev kalıcı olarak silindi"),
            @ApiResponse(responseCode = "404", description = "Görev bulunamadı")
    })
    public ResponseEntity<Void> hardDeleteTask(
            @Parameter(description = "Görev ID'si", required = true) @PathVariable UUID id) {
        taskService.hardDeleteTask(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/exists/title/{title}")
    @Operation(summary = "Başlık kontrolü", description = "Belirli bir başlığa sahip görevin var olup olmadığını kontrol eder")
    @ApiResponse(responseCode = "200", description = "Kontrol başarıyla tamamlandı")
    public ResponseEntity<Boolean> existsByTitle(
            @Parameter(description = "Görev başlığı", required = true) @PathVariable String title) {
        boolean exists = taskService.existsByTitle(title);
        return ResponseEntity.ok(exists);
    }

    @PostMapping("/search")
    @Operation(summary = "Görevlerde gelişmiş arama", description = "Birden fazla kritere göre görev arama. Tüm parametreler isteğe bağlıdır.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarıyla tamamlandı",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz arama parametreleri")
    })
    public ResponseEntity<PageResponse<TaskResponse>> searchTasks(
            @Parameter(description = "Arama kriterleri") @RequestBody(required = false) SearchTaskRequest request,
            @Parameter(description = "Sayfa numarası (0'dan başlar)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama (alan,yön formatında)") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        SearchTaskRequest searchRequest = request != null ? request : new SearchTaskRequest();
        PageResponse<TaskResponse> response = taskService.searchTasks(searchRequest, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Görevlerde basit arama", description = "Başlık ve açıklamada anahtar kelime arama")
    @ApiResponse(responseCode = "200", description = "Arama başarıyla tamamlandı")
    public ResponseEntity<PageResponse<TaskResponse>> searchTasksByQuery(
            @Parameter(description = "Arama kelimesi", required = true) @RequestParam String query,
            @Parameter(description = "Sayfa numarası") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa başına öğe sayısı") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = createPageable(page, size, sort);
        PageResponse<TaskResponse> response = taskService.searchTasksByQuery(query, pageable);
        return ResponseEntity.ok(response);
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
