package org.task.taskmaganer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.task.taskmaganer.dto.request.CreateUserRequest;
import org.task.taskmaganer.dto.request.UpdateUserRequest;
import org.task.taskmaganer.dto.response.UserResponse;
import org.task.taskmaganer.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/users", name = "UserController")
@Tag(name = "User Management", description = "Kullanıcı yönetimi işlemleri için API endpointleri")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/")
    @Operation(summary = "Yeni kullanıcı oluştur", description = "Yeni bir kullanıcı oluşturur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Kullanıcı başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek"),
            @ApiResponse(responseCode = "409", description = "Kullanıcı zaten mevcut")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Kullanıcıyı ID'ye göre getir", description = "Belirli bir ID'ye sahip kullanıcıyı getirir")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı bulundu",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Kullanıcıyı kullanıcı adına göre getir", description = "Kullanıcı adına göre kullanıcıyı getirir")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı bulundu"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    public ResponseEntity<UserResponse> getUserByUsername(
            @Parameter(description = "Kullanıcı adı", required = true) @PathVariable String username) {
        UserResponse response = userService.getUserByUsername(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Kullanıcıyı e-postaya göre getir", description = "E-posta adresine göre kullanıcıyı getirir")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı bulundu"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    public ResponseEntity<UserResponse> getUserByEmail(
            @Parameter(description = "E-posta adresi", required = true) @PathVariable String email) {
        UserResponse response = userService.getUserByEmail(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    @Operation(summary = "Tüm kullanıcıları getir", description = "Sistemdeki tüm kullanıcıları getirir")
    @ApiResponse(responseCode = "200", description = "Kullanıcılar başarıyla getirildi")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> responses = userService.getAllUsers();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/active")
    @Operation(summary = "Aktif kullanıcıları getir", description = "Silinmemiş tüm aktif kullanıcıları getirir")
    @ApiResponse(responseCode = "200", description = "Aktif kullanıcılar başarıyla getirildi")
    public ResponseEntity<List<UserResponse>> getAllActiveUsers() {
        List<UserResponse> responses = userService.getAllActiveUsers();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Kullanıcıyı güncelle", description = "Mevcut bir kullanıcıyı günceller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı başarıyla güncellendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek")
    })
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Kullanıcıyı sil (soft delete)", description = "Kullanıcıyı soft delete yapar (deleted_at alanını doldurur)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/hard")
    @Operation(summary = "Kullanıcıyı kalıcı sil", description = "Kullanıcıyı veritabanından tamamen siler")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kullanıcı kalıcı olarak silindi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    public ResponseEntity<Void> hardDeleteUser(
            @Parameter(description = "Kullanıcı ID'si", required = true) @PathVariable UUID id) {
        userService.hardDeleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/exists/username/{username}")
    @Operation(summary = "Kullanıcı adı kontrolü", description = "Belirli bir kullanıcı adının var olup olmadığını kontrol eder")
    @ApiResponse(responseCode = "200", description = "Kontrol başarıyla tamamlandı")
    public ResponseEntity<Boolean> existsByUsername(
            @Parameter(description = "Kullanıcı adı", required = true) @PathVariable String username) {
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/exists/email/{email}")
    @Operation(summary = "E-posta kontrolü", description = "Belirli bir e-posta adresinin var olup olmadığını kontrol eder")
    @ApiResponse(responseCode = "200", description = "Kontrol başarıyla tamamlandı")
    public ResponseEntity<Boolean> existsByEmail(
            @Parameter(description = "E-posta adresi", required = true) @PathVariable String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }
}