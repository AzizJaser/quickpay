package com.quickpay.notification.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handlerCustomerNotFoundException(CustomerNotFoundException e){
        logger.error("customer with cif ={} was not found",e.getCif());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Customer Not Found");
        return problem;
    }

    @ExceptionHandler(NotificationTypeNotFoundException.class)
    public ProblemDetail handlerNotificationTypeNotFoundException(NotificationTypeNotFoundException e){
        logger.error("notification with routing key :"+e.getKey()+" is not found");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Routing Key is Not Found");
        return problem;
    }
}
