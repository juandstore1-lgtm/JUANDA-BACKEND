package com.jdqstore.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Error con un código HTTP distinto de 400 (p. ej. 429, 503) y un mensaje apto para mostrar al usuario. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
