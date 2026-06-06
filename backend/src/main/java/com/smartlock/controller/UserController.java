package com.smartlock.controller;

import com.smartlock.dto.*;
import com.smartlock.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sendCode")
    public ApiResponse<?> sendCode(@RequestParam String username) {
        userService.sendCode(username);
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/info")
    public ApiResponse<LoginResponse.UserInfo> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        LoginResponse.UserInfo userInfo = userService.getUserInfo(userId);
        return ApiResponse.success(userInfo);
    }
}
