package com.jdqstore.backend.auth;

import com.jdqstore.backend.dto.AuthRequest;
import com.jdqstore.backend.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }
}
