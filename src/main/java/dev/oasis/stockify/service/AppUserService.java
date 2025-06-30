package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.mapper.UserMapper;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing user operations
 */
@Service
@Slf4j
public class AppUserService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AppUserService(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          UserMapper userMapper) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }    /**
     * Check if username exists in current tenant
     *
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    public boolean existsByUsername(String username) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("👥 Checking if username '{}' exists for tenant: {}", username, currentTenant);
        
        // First try tenant-aware check
        if (currentTenant != null && !currentTenant.isEmpty()) {
            boolean existsInTenant = appUserRepository.existsByUsernameAndPrimaryTenant(username, currentTenant);
            if (existsInTenant) {
                return true;
            }
        }
        
        // Fallback to general username check (for multi-tenant schema)
        return appUserRepository.existsByUsername(username);
    }

    /**
     * Check if email exists in current tenant
     *
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    public boolean existsByEmail(String email) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📧 Checking if email '{}' exists for tenant: {}", email, currentTenant);
        
        // Check if email exists across all tenants or in current tenant
        try {
            return appUserRepository.existsByEmail(email);
        } catch (Exception e) {
            log.warn("Could not check email existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Saves a user to the database
     *
     * @param userCreateDTO the user data to save
     * @return the saved user data
     */
    public UserResponseDTO saveUser(UserCreateDTO userCreateDTO) {
        // Check if username already exists
        if (existsByUsername(userCreateDTO.getUsername())) {
            throw new RuntimeException("Kullanıcı adı zaten mevcut: " + userCreateDTO.getUsername());
        }
        
        if (userCreateDTO.getPassword() == null || userCreateDTO.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Şifre boş olamaz");
        }

        AppUser appUser = userMapper.toEntity(userCreateDTO);
        appUser.setPassword(passwordEncoder.encode(userCreateDTO.getPassword()));
        AppUser savedUser = appUserRepository.save(appUser);
        return userMapper.toDto(savedUser);
    }    /**
     * Creates a user and returns the entity (for super admin operations)
     *
     * @param userCreateDTO the user data to create
     * @return the created user entity
     */
    public AppUser createUser(UserCreateDTO userCreateDTO) {
        // Check if username already exists
        if (existsByUsername(userCreateDTO.getUsername())) {
            throw new RuntimeException("Kullanıcı adı zaten mevcut: " + userCreateDTO.getUsername());
        }
        
        if (userCreateDTO.getPassword() == null || userCreateDTO.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Şifre boş olamaz");
        }

        AppUser appUser = userMapper.toEntity(userCreateDTO);
        appUser.setPassword(passwordEncoder.encode(userCreateDTO.getPassword()));
        return appUserRepository.save(appUser);
    }/**
     * Retrieves all active users from the database for the current tenant
     *
     * @return a list of all active users for the current tenant
     */
    public List<UserResponseDTO> getAllUsers() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("👥 Getting all active users for tenant: {}", currentTenant);
        
        List<AppUser> users = appUserRepository.findAllActiveUsers();
        log.debug("👥 Found {} active users for tenant: {}", users.size(), currentTenant);
        
        // Note: This is tenant-aware because the repository automatically filters by tenant
        // due to the multi-tenant configuration. Also filtering out inactive users.
        return users.stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }    /**
     * Retrieves a page of active users from the database
     *
     * @param pageable pagination information
     * @return a page of active users
     */
    public Page<UserResponseDTO> getUsersPage(Pageable pageable) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("👥 Getting active users page for tenant: {}, pageable: {}", currentTenant, pageable);
        
        Page<AppUser> userPage = appUserRepository.findAllActiveUsers(pageable);
        log.debug("👥 Found {} active users in database for tenant: {}", userPage.getTotalElements(), currentTenant);
        
        List<UserResponseDTO> userDtos = userPage.getContent().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
        
        log.debug("👥 Converted {} users to DTOs", userDtos.size());
        return new PageImpl<>(userDtos, pageable, userPage.getTotalElements());
    }    /**
     * Search active users by username or email
     *
     * @param search the search term
     * @param pageable pagination information
     * @return a page of matching active users
     */
    public Page<UserResponseDTO> searchUsers(String search, Pageable pageable) {
        List<AppUser> allActiveUsers = appUserRepository.findAllActiveUsers();
        List<AppUser> filteredUsers = allActiveUsers.stream()
                .filter(user -> user.getUsername().toLowerCase().contains(search.toLowerCase()) ||
                               (user.getEmail() != null && user.getEmail().toLowerCase().contains(search.toLowerCase())))
                .collect(Collectors.toList());
        
        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredUsers.size());
        List<AppUser> pageContent = filteredUsers.subList(start, end);
        
        List<UserResponseDTO> userDtos = pageContent.stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
        
        return new PageImpl<>(userDtos, pageable, filteredUsers.size());
    }

    /**
     * Get user by ID
     *
     * @param id the user ID
     * @return the user DTO
     */
    public UserResponseDTO getUserById(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + id));
        return userMapper.toDto(user);
    }    /**
     * Update an existing user
     *
     * @param id the user ID to update
     * @param userCreateDTO the updated user data
     * @return the updated user DTO
     */
    public UserResponseDTO updateUser(Long id, UserCreateDTO userCreateDTO) {
        AppUser existingUser = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + id));
        
        // Check if username is being changed and if new username already exists
        if (!existingUser.getUsername().equals(userCreateDTO.getUsername()) && 
            existsByUsername(userCreateDTO.getUsername())) {
            throw new RuntimeException("Kullanıcı adı zaten mevcut: " + userCreateDTO.getUsername());
        }
        
        // Update fields
        existingUser.setUsername(userCreateDTO.getUsername());
        existingUser.setEmail(userCreateDTO.getEmail());
        existingUser.setRole(userCreateDTO.getRole());
        existingUser.setIsActive(userCreateDTO.getActive());
        
        // Only update password if provided
        if (userCreateDTO.getPassword() != null && !userCreateDTO.getPassword().trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userCreateDTO.getPassword()));
        }
        
        AppUser updatedUser = appUserRepository.save(existingUser);
        return userMapper.toDto(updatedUser);
    }

    /**
     * Toggle user active status
     *
     * @param id the user ID
     */
    public void toggleUserStatus(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + id));
        user.setIsActive(!user.getIsActive());
        appUserRepository.save(user);
    }

    /**
     * Delete user (soft delete by setting inactive)
     *
     * @param id the user ID
     */
    public void deleteUser(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + id));
        user.setIsActive(false);
        appUserRepository.save(user);
    }

    /**
     * Bulk activate users
     *
     * @param userIds list of user IDs to activate
     */
    public void bulkActivateUsers(List<Long> userIds) {
        List<AppUser> users = appUserRepository.findAllById(userIds);
        users.forEach(user -> user.setIsActive(true));
        appUserRepository.saveAll(users);
    }

    /**
     * Bulk deactivate users
     *
     * @param userIds list of user IDs to deactivate
     */
    public void bulkDeactivateUsers(List<Long> userIds) {
        List<AppUser> users = appUserRepository.findAllById(userIds);
        users.forEach(user -> user.setIsActive(false));
        appUserRepository.saveAll(users);
    }

    /**
     * Bulk delete users (soft delete)
     *
     * @param userIds list of user IDs to delete
     */
    public void bulkDeleteUsers(List<Long> userIds) {
        List<AppUser> users = appUserRepository.findAllById(userIds);
        users.forEach(user -> user.setIsActive(false));
        appUserRepository.saveAll(users);
    }

    /**
     * Count active users in current tenant
     */
    public long countActiveUsers() {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null && !currentTenant.isEmpty()) {
            return appUserRepository.countByPrimaryTenantAndIsActive(currentTenant, true);
        }
        return appUserRepository.countByIsActive(true);
    }
}
