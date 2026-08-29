package com.jdqstore.backend.auth;

import com.jdqstore.backend.dto.AuthRequest;
import com.jdqstore.backend.dto.AuthResponse;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.security.JwtService;
import com.jdqstore.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow();
        var userDetails = new UserDetailsImpl(user);
        var jwtToken = jwtService.generateToken(new HashMap<>(), userDetails);
        
        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole().getName())
                .storeIds(user.getStores() != null ? user.getStores().stream().map(s -> s.getId()).toList() : new java.util.ArrayList<>())
                .build();
    }
}
