package org.task.taskmaganer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.task.taskmaganer.entity.User;

import java.time.LocalDateTime;
import java.util.Objects;

@Schema(description = "Kullanıcı yanıt modeli")
public class UserResponse {
    
    @Schema(description = "Kullanıcı ID'si", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Kullanıcı adı", example = "johndoe")
    private String username;

    @Schema(description = "E-posta adresi", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Ad", example = "John")
    private String firstName;

    @Schema(description = "Soyad", example = "Doe")
    private String lastName;

    @Schema(description = "Kullanıcı aktiflik durumu", example = "true")
    private Boolean isActive;

    @Schema(description = "Oluşturulma tarihi", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Güncellenme tarihi", example = "2024-01-15T14:45:00")
    private LocalDateTime updatedAt;
    
    public UserResponse() {}
    
    public UserResponse(String id, String username, String email, String firstName, String lastName, 
                       Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    public UserResponse(User user) {
        this.id = user.getId().toString();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.isActive = user.getIsActive();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserResponse that = (UserResponse) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(username, that.username) && 
               Objects.equals(email, that.email) && 
               Objects.equals(firstName, that.firstName) && 
               Objects.equals(lastName, that.lastName) && 
               Objects.equals(isActive, that.isActive) && 
               Objects.equals(createdAt, that.createdAt) && 
               Objects.equals(updatedAt, that.updatedAt);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, username, email, firstName, lastName, isActive, createdAt, updatedAt);
    }
}