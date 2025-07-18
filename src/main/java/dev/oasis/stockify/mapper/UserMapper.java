package dev.oasis.stockify.mapper;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Role;
import org.springframework.stereotype.Component;

/**
 * Mapper class for converting between AppUser entity and User DTOs
 */
@Component
public class UserMapper {

    /**
     * Converts a UserCreateDTO to an AppUser entity
     * @param userCreateDTO the DTO to convert
     * @return the AppUser entity
     */
    public AppUser toEntity(UserCreateDTO userCreateDTO) {
        if (userCreateDTO == null) {
            return null;
        }        AppUser appUser = new AppUser();
        appUser.setUsername(userCreateDTO.getUsername());
        appUser.setPassword(userCreateDTO.getPassword());
        appUser.setRole(userCreateDTO.getRole());
        appUser.setEmail(userCreateDTO.getEmail());
        appUser.setIsActive(userCreateDTO.getActive());
        appUser.setPrimaryTenant(userCreateDTO.getPrimaryTenant());
        
        // Set tenant-specific fields based on role
        if (userCreateDTO.getRole() == Role.SUPER_ADMIN) {
            // Super admin can access all tenants
            appUser.setCanManageAllTenants(true);
            appUser.setIsGlobalUser(true);
            // Use accessibleTenants from DTO if provided, otherwise use primaryTenant
            appUser.setAccessibleTenants(userCreateDTO.getAccessibleTenants() != null ? 
                                       userCreateDTO.getAccessibleTenants() : 
                                       userCreateDTO.getPrimaryTenant());
        } else {
            // Regular users only access their own tenant
            appUser.setCanManageAllTenants(false);
            appUser.setIsGlobalUser(false);
            // Use accessibleTenants from DTO if provided, otherwise use primaryTenant
            appUser.setAccessibleTenants(userCreateDTO.getAccessibleTenants() != null ? 
                                       userCreateDTO.getAccessibleTenants() : 
                                       userCreateDTO.getPrimaryTenant());
        }
        
        return appUser;
    }

    /**
     * Updates an existing AppUser entity with data from a UserCreateDTO
     * @param appUser the entity to update
     * @param userCreateDTO the DTO with updated data
     * @return the updated AppUser entity
     */
    public AppUser updateEntity(AppUser appUser, UserCreateDTO userCreateDTO) {
        if (userCreateDTO == null) {
            return appUser;
        }

        appUser.setUsername(userCreateDTO.getUsername());        appUser.setPassword(userCreateDTO.getPassword());
        appUser.setRole(userCreateDTO.getRole());
        appUser.setEmail(userCreateDTO.getEmail());
        appUser.setIsActive(userCreateDTO.getActive());
        appUser.setPrimaryTenant(userCreateDTO.getPrimaryTenant());
        
        // Update tenant-specific fields based on role
        if (userCreateDTO.getRole() == Role.SUPER_ADMIN) {
            // Super admin can access all tenants
            appUser.setCanManageAllTenants(true);
            appUser.setIsGlobalUser(true);
            // Use accessibleTenants from DTO if provided, otherwise use primaryTenant
            appUser.setAccessibleTenants(userCreateDTO.getAccessibleTenants() != null ? 
                                       userCreateDTO.getAccessibleTenants() : 
                                       userCreateDTO.getPrimaryTenant());
        } else {
            // Regular users only access their own tenant
            appUser.setCanManageAllTenants(false);
            appUser.setIsGlobalUser(false);
            // Use accessibleTenants from DTO if provided, otherwise use primaryTenant
            appUser.setAccessibleTenants(userCreateDTO.getAccessibleTenants() != null ? 
                                       userCreateDTO.getAccessibleTenants() : 
                                       userCreateDTO.getPrimaryTenant());
        }
        
        return appUser;
    }    /**
     * Converts an AppUser entity to a UserResponseDTO
     * @param appUser the entity to convert
     * @return the UserResponseDTO
     */
    public UserResponseDTO toDto(AppUser appUser) {
        if (appUser == null) {
            return null;
        }

        UserResponseDTO userResponseDTO = new UserResponseDTO();
        userResponseDTO.setId(appUser.getId());        userResponseDTO.setUsername(appUser.getUsername());
        userResponseDTO.setRole(appUser.getRole());
        userResponseDTO.setEmail(appUser.getEmail());
        userResponseDTO.setActive(appUser.getIsActive());
        userResponseDTO.setCreatedAt(appUser.getCreatedAt());
        
        return userResponseDTO;
    }
}