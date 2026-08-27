package com.quickpay.bill.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(ReserveDeclinedException.class)
    public ProblemDetail handlerWalletNotFoundException(ReserveDeclinedException e){
        logger.warn("reserve amount declined with message {}",e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Reserve Declined");
        return problem;
    }

    @ExceptionHandler(BillNotFoundException.class)
    public ProblemDetail handlerBillNotFoundException(BillNotFoundException e){
        logger.warn("Payment request with number not found {}",e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Bill is not found");
        return problem;
    }

    @ExceptionHandler(SettleRejectedException.class)
    public ProblemDetail handlerSettleRejectedException(SettleRejectedException e){
        logger.warn("Settlement of Payment number {} is rejected",e.getPaymentId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Payment is rejected");
        return problem;
    }

    @ExceptionHandler(FundIsReleasedException.class)
    public ProblemDetail handlerFundIsReleasedException(FundIsReleasedException e){
        logger.warn("Payment number {} is released and the customer is refunded",e.getPaymentId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Payment is released");
        return problem;
    }
}
