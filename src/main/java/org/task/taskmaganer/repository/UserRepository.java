package org.task.taskmaganer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.task.taskmaganer.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.id = :id")
    Optional<User> findActiveUserById(@Param("id") UUID id);
    
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    java.util.List<User> findAllActiveUsers();
}