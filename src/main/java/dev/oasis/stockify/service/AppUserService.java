package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.mapper.UserMapper;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
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
    }

    /**
     * Saves a user to the database
     *
     * @param userCreateDTO the user data to save
     * @return the saved user data
     */
    public UserResponseDTO saveUser(UserCreateDTO userCreateDTO) {
        AppUser appUser = userMapper.toEntity(userCreateDTO);
        appUser.setPassword(passwordEncoder.encode(appUser.getPassword()));
        AppUser savedUser = appUserRepository.save(appUser);
        return userMapper.toDto(savedUser);
    }

    /**
     * Creates a user and returns the entity (for super admin operations)
     *
     * @param userCreateDTO the user data to create
     * @return the created user entity
     */
    public AppUser createUser(UserCreateDTO userCreateDTO) {
        AppUser appUser = userMapper.toEntity(userCreateDTO);
        appUser.setPassword(passwordEncoder.encode(appUser.getPassword()));
        return appUserRepository.save(appUser);
    }

    /**
     * Retrieves all users from the database
     *
     * @return a list of all users
     */
    public List<UserResponseDTO> getAllUsers() {
        List<AppUser> users = appUserRepository.findAll();
        return users.stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a page of users from the database
     *
     * @param pageable pagination information
     * @return a page of users
     */
    public Page<UserResponseDTO> getUsersPage(Pageable pageable) {
        Page<AppUser> userPage = appUserRepository.findAll(pageable);
        List<UserResponseDTO> userDtos = userPage.getContent().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
        return new PageImpl<>(userDtos, pageable, userPage.getTotalElements());
    }
}
