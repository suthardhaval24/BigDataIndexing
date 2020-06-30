package com.bigDataIndexing.FinalDemo.exception;

/* dhaval created on 3/14/20 inside the package - com.bigDataIndexing.FinalDemo.exception */

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.EXPECTATION_FAILED)
public class ExpectationFailed extends RuntimeException {
    private String message;
    private HttpStatus status = HttpStatus.EXPECTATION_FAILED;

    public ExpectationFailed(String message) {
        super(String.format("%s", message));
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}