package com.yourcompany.etsystoktakip.config;

import com.yourcompany.etsystoktakip.controller.LoginController;
import com.yourcompany.etsystoktakip.controller.ProductController;
import com.yourcompany.etsystoktakip.controller.UserController;
import com.yourcompany.etsystoktakip.exception.CustomException;
import com.yourcompany.etsystoktakip.exception.GlobalExceptionHandler;
import com.yourcompany.etsystoktakip.service.AppUserDetailsService;
import com.yourcompany.etsystoktakip.service.AppUserService;
import com.yourcompany.etsystoktakip.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private AppUserService appUserService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(), new ProductController(), new UserController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Mocking services to prevent NullPointerException and simulate exceptions
        when(productService.saveProduct(any())).thenThrow(new IllegalArgumentException("Mocked exception"));
        when(appUserService.getAllUsers()).thenThrow(new CustomException("Mocked exception"));
    }

    @WithMockUser
    @Test
    public void testHandleUsernameNotFoundException() throws Exception {
        mockMvc.perform(get("/users/list"))
                .andDo(result -> System.out.println("Response: " + result.getResponse().getContentAsString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @WithMockUser
    @Test
    public void testHandleBadCredentialsException() throws Exception {
        mockMvc.perform(post("/login").param("username", "wrong").param("password", "wrong"))
                .andDo(result -> System.out.println("Response: " + result.getResponse().getContentAsString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication Failed"));
    }

    @WithMockUser
    @Test
    public void testHandleAccessDeniedException() throws Exception {
        mockMvc.perform(get("/products/forbidden"))
                .andDo(result -> System.out.println("Response: " + result.getResponse().getContentAsString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @WithMockUser
    @Test
    public void testHandleValidationExceptions() throws Exception {
        mockMvc.perform(post("/products/add").content("{}")) // Adjusted endpoint
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @WithMockUser
    @Test
    public void testHandleHttpRequestMethodNotSupportedException() throws Exception {
        mockMvc.perform(put("/products")) // Adjusted endpoint
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @WithMockUser
    @Test
    public void testHandleHttpMessageNotReadableException() throws Exception {
        mockMvc.perform(post("/products/add").content("invalid-json")) // Adjusted endpoint
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON"))
                .andDo(result -> System.out.println("Response: " + result.getResponse().getContentAsString()));
    }
}
