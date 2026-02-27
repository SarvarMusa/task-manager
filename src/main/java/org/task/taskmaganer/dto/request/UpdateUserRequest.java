package org.task.taskmaganer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;

@Schema(description = "Kullanıcı güncelleme isteği")
public class UpdateUserRequest {
    
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Schema(description = "Ad", example = "John", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Schema(description = "Soyad", example = "Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;
    
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "E-posta adresi", example = "john.doe@example.com")
    private String email;
    
    @Size(min = 6, message = "Password must be at least 6 characters long")
    @Schema(description = "Şifre (en az 6 karakter, boş bırakılırsa değişmez)", example = "newpassword123")
    private String password;
    
    @Schema(description = "Kullanıcı aktiflik durumu", example = "true")
    private Boolean isActive;
    
    public UpdateUserRequest() {}
    
    public UpdateUserRequest(String firstName, String lastName, String email, String password, Boolean isActive) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.isActive = isActive;
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
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
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
        UpdateUserRequest that = (UpdateUserRequest) o;
        return Objects.equals(firstName, that.firstName) && 
               Objects.equals(lastName, that.lastName) && 
               Objects.equals(email, that.email) && 
               Objects.equals(password, that.password) && 
               Objects.equals(isActive, that.isActive);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email, password, isActive);
    }
}