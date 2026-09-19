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

    @ExceptionHandler(CustomerIsNotActiveException.class)
    public ProblemDetail handlerCustomerIsNotActiveException(CustomerIsNotActiveException e){
        logger.warn("Unsuccessful logging attempt due to status for customer with email: {}",e.getEmail());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Customer status is not active");
        return problem;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handlerCustomerNotFoundException(CustomerNotFoundException e){
        logger.warn("customer was not found");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Customer was not found");
        return problem;
    }

    @ExceptionHandler(CustomerClosedException.class)
    public ProblemDetail handlerCustomerClosedException(CustomerClosedException e){
        logger.warn("customer is closed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Customer is closed");
        return problem;
    }

    @ExceptionHandler(PasswordNotValidException.class)
    public ProblemDetail handlerPasswordNotValidException(PasswordNotValidException e){
        logger.warn("Password is not correct");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Password is not correct");
        return problem;
    }

    @ExceptionHandler(HashingTokenException.class)
    public ProblemDetail handlerHashingTokenException(HashingTokenException e){
        logger.warn("Error while generating hashed token");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        problem.setTitle("Cannot generate session");
        return problem;
    }

    @ExceptionHandler(SessionIsNotFoundOrExpiredException.class)
    public ProblemDetail handlerSessionIsNotFoundOrExpiredException(SessionIsNotFoundOrExpiredException e){
        logger.warn("session is not found or expired");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("session not found or expired");
        return problem;
    }

    @ExceptionHandler(MissingSessionException.class)
    public ProblemDetail handlerMissingSessionException(MissingSessionException e){
        logger.warn("Unauthorize attempt, request without a session");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("no auth header");
        return problem;
    }

}
