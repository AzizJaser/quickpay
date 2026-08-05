package com.quickpay.notification.exception;

public class CustomerNotFound extends RuntimeException {
  public CustomerNotFound(String message) {
    super(message);
  }
}
