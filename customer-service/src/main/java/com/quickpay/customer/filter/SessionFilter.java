package com.quickpay.customer.filter;

import com.quickpay.customer.domain.Session;
import com.quickpay.customer.exception.MissingSessionException;
import com.quickpay.customer.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(2)
@Component
@RequiredArgsConstructor
public class SessionFilter extends OncePerRequestFilter {

    private static final String MDC_CIF_KEY = "cif";
//    private static final String MDC_SESSION_ID = "sessionId" no need for now

    private final AuthService authService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if(publicURLs(request.getRequestURI())){
            doFilter(request,response,filterChain);
            return;
        }

        String header = request.getHeader("Authorization");
        if(header == null || !header.startsWith("Bearer ")){
            response.setStatus(401);
            return;
        }
        String token = header.substring(7); // after "Bearer " -> 7 spaces
        Session session;
        try {
            session = authService.validateSession(token);
//            request.setAttribute("cif", session.getCif());
//            request.setAttribute("sessionId", session.getSessionId());
        } catch (Exception e) {
            logger.warn(e.getMessage());
            response.setStatus(401);
            return;
        }
        request.setAttribute("cif",session.getCif());
        request.setAttribute("sessionId", session.getSessionId());
        MDC.put(MDC_CIF_KEY,session.getCif());
        try {
            doFilter(request,response,filterChain);
        } finally {
            MDC.remove(MDC_CIF_KEY);
        }
    }


    private boolean publicURLs(String url){
        return switch (url) {
            case "/v1/customer", "/v1/customer/activate", "/v1/login" -> true;
            case null, default -> false;
        };
    }
}
