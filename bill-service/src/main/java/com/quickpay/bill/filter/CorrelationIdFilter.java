package com.quickpay.bill.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if(request.getHeader(CORRELATION_HEADER) != null && !request.getHeader(CORRELATION_HEADER).isBlank()){ //if the correlation is present

            MDC.put(MDC_KEY,request.getHeader(CORRELATION_HEADER));

            try {
                filterChain.doFilter(request,response);
            } finally {
                MDC.clear();
            }
        } else { // if not correlation present
            String correlationId = UUID.randomUUID().toString().substring(0,8);

            MDC.put(MDC_KEY,correlationId);

            response.setHeader(CORRELATION_HEADER,correlationId);

            try {
                filterChain.doFilter(request,response);
            } finally {
                MDC.clear();
            }
        }

    }
}
