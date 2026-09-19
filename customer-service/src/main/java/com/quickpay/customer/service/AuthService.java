package com.quickpay.customer.service;

import com.quickpay.customer.domain.Customer;
import com.quickpay.customer.domain.Session;
import com.quickpay.customer.dto.request.LoginRequest;
import com.quickpay.customer.dto.response.LoginResponse;
import com.quickpay.customer.enums.CustomerStatus;
import com.quickpay.customer.exception.CustomerIsNotActiveException;
import com.quickpay.customer.exception.HashingTokenException;
import com.quickpay.customer.exception.PasswordNotValidException;
import com.quickpay.customer.exception.SessionIsNotFoundOrExpiredException;
import com.quickpay.customer.repository.CustomerRepository;
import com.quickpay.customer.repository.SessionRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Encoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SessionRepository sessionRepository;

    private final CustomerRepository customerRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${customer.session-ttl-minutes}")
    private long ttl;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Transactional
    public LoginResponse login(LoginRequest request){

        Customer customer = customerRepository.findByEmailAndStatus(request.email(), CustomerStatus.ACTIVE)
                .orElseThrow( () -> new CustomerIsNotActiveException(request.email()));

        if(!passwordEncoder.matches(request.password(), customer.getPassword())){
            throw new PasswordNotValidException();
        }

        String token = generateToken();
        String tokenHash = tokenHasher(token);

        Session session = new Session(UUID.randomUUID().toString(), tokenHash, customer.getCif(), null, Timestamp.valueOf(LocalDateTime.now().plusMinutes(ttl)));
        sessionRepository.save(session);
        return new LoginResponse(token);
    }

    private String generateToken(){
        byte[] buf = new byte[32];
        SecureRandom token = new SecureRandom();
        token.nextBytes(buf); // generate 32 random bytes
        String stringToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        return stringToken;
    }

    private String tokenHasher(String input) {
        try {
            byte[] bytesToken = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); // the hashing
            String hashToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesToken);
            return hashToken;
        }  catch (NoSuchAlgorithmException e){
            logger.error("error while generating token in login method");
            throw new HashingTokenException();
        }

    }

    public String validateSession(String token){
        String hash = tokenHasher(token);
        Session session = sessionRepository.findByTokenHashAndExpiresAtAfter(hash,Timestamp.valueOf(LocalDateTime.now()))
                .orElseThrow(SessionIsNotFoundOrExpiredException::new);

        return session.getCif();
    }

}