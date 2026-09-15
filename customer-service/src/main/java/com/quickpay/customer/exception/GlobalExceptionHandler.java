package com.quickpay.customer.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(DuplicateNationalIdException.class)
    public ProblemDetail handlerDuplicateNationalIdException(DuplicateNationalIdException e){
        logger.warn("customer with national id ={} is already exists",e.getNationalId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Duplicate national id Found");
        return problem;
    }

    @ExceptionHandler(ParsingCustomerEventException.class)
    public ProblemDetail handlerParsingCustomerEventException(ParsingCustomerEventException e){
        logger.warn("error when trying to pars customer registration event with cif: {}",e.getCif());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("serialization customer error");
        return problem;
    }

}
