package com.quickpay.wallet.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handlerWalletNotFoundException(WalletNotFoundException e){
        logger.warn("wallet with number ={} was not found",e.getWalletNumber());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Wallet Not Found");
        return problem;
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ProblemDetail handlerInsufficientBalanceException(InsufficientBalanceException e){
        logger.warn("insufficient balance for wallet with number ={}",e.getWalletNumber());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("insufficient balance");
        return problem;
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ProblemDetail handlerInvalidAmountException(InvalidAmountException e){
        logger.warn("invalid amount entered");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("invalid amount entered");
        return problem;
    }

    @ExceptionHandler(DuplicatedEntryException.class)
    public ProblemDetail handlerDuplicatedEntryException(DuplicatedEntryException e){
        logger.warn("Duplicated entry with same key ={}",e.getIdempotencyKey());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("duplicated entry");
        return problem;
    }

    @ExceptionHandler(ConflictEntryException.class)
    public ProblemDetail handlerConflictEntryException(ConflictEntryException e){
        logger.warn("debited wallet is the credited wallet, please make sure debited wallet is different than credited wallet.");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("conflict entry");
        return problem;
    }

    @ExceptionHandler(WalletNotActiveException.class)
    public ProblemDetail handlerWalletNotActiveException(WalletNotActiveException e){
        logger.warn("Wallet with number {} is {}",e.getWallet_number(),e.getStatus());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Wallet is not active");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handlerDataIntegrityViolationException(DataIntegrityViolationException e){
        logger.warn("data integrity violation",e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"request conflicts with existing data");
        problemDetail.setTitle("conflict");
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception e) {
        logger.error("unexpected error", e);            // log EVERYTHING server-side
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");  // GENERIC to the client
        p.setTitle("internal error");
        return p;
    }

    @ExceptionHandler(NumberOfWalletsExceededException.class)
    public ProblemDetail handlerNumberOfWalletsExceededException(NumberOfWalletsExceededException e){
        logger.warn("Number of wallets exceeded while creating",e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"number of wallets exceeded");
        problemDetail.setTitle("number of wallets violation");
        return problemDetail;
    }
    @ExceptionHandler(WalletNumberAllocationException.class)
    public ProblemDetail handlerWalletNumberAllocationException(WalletNumberAllocationException e){
        logger.warn("Number of retries exceeded while creating",e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"number of retries exceeded");
        problemDetail.setTitle("number of retries exceeded");
        return problemDetail;
    }

}
