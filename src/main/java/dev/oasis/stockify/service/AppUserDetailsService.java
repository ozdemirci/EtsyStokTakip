package dev.oasis.stockify.service;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    
    @Autowired
    private AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        try {
            if (username == null || username.trim().isEmpty()) {
                String errorMsg = "Username cannot be empty";
                throw new UsernameNotFoundException(errorMsg);
            }

            AppUser appUser = appUserRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        String errorMsg = "User not found in database: " + username;
                        return new UsernameNotFoundException(errorMsg);
                    });

            UserDetails userDetails = User.withUsername(appUser.getUsername())
                    .password(appUser.getPassword())
                    .roles(appUser.getRole().toUpperCase())
                    .build();

            return userDetails;
        } catch (Exception e) {
            throw e;

        }
    }
}