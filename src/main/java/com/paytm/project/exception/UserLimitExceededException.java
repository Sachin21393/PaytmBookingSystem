package com.paytm.project.exception;

public class UserLimitExceededException extends RuntimeException {
    public UserLimitExceededException(String message) {
        super(message);
    }
}
