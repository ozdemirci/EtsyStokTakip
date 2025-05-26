// örnek controller
// filepath: src/main/java/com/yourcompany/etsystoktakip/controller/LoginController.java
package com.yourcompany.etsystoktakip.controller;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;


@Controller
public class LoginController {
    @GetMapping("/login")
    public String login() {
        return "login"; // templates/login.html
    }
    
    @PostMapping("/login")
    public void simulateBadCredentials() {
        System.out.println("Simulating BadCredentialsException");
        throw new BadCredentialsException("Invalid username or password");
    }
}