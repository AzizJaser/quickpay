package com.quickpay.customer.web;

import com.quickpay.customer.dto.request.LoginRequest;
import com.quickpay.customer.dto.response.LoginResponse;
import com.quickpay.customer.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestAttribute("sessionId") String sessionId){
        authService.logout(sessionId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
