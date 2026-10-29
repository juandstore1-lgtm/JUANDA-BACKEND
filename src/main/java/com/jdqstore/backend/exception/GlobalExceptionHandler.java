package com.jdqstore.backend.exception;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
    
    // Mensajes de negocio que forman parte del contrato actual y que muestra el frontend: las lanza el código de
    // la aplicación ("Ticket 00 is no longer available", "No value present") o la autenticación ("Bad credentials")
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, NoSuchElementException.class,
            AuthenticationException.class})
    public ResponseEntity<Map<String, String>> handleBusinessException(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Acceso denegado en {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.FORBIDDEN, "Acceso denegado");
    }

    // Errores de base de datos: el mensaje lleva SQL, nombres de restricciones y valores enviados; se mantiene el 400
    @ExceptionHandler({DataAccessException.class, PersistenceException.class, HibernateException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseException(RuntimeException ex, HttpServletRequest request) {
        log.warn("Error de base de datos en {} {}: {}", request.getMethod(), request.getRequestURI(), describe(ex));
        return error(HttpStatus.BAD_REQUEST, "No se pudo guardar la información enviada");
    }

    // Resto de errores (JSON mal formado, tipos de parámetro, errores internos): mensaje genérico con el mismo código
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.warn("Solicitud rechazada en {} {}: {}", request.getMethod(), request.getRequestURI(), describe(ex));
        return error(HttpStatus.BAD_REQUEST, "Solicitud no válida");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}: {}", request.getMethod(), request.getRequestURI(), describe(ex));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return new ResponseEntity<>(response, status);
    }

    /**
     * Tipo de cada excepción de la cadena, SQLState y la línea donde se lanzó. No incluye los mensajes: pueden
     * contener datos enviados por el cliente (correos, teléfonos) o valores internos.
     */
    public static String describe(Throwable ex) {
        StringBuilder description = new StringBuilder();
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (depth > 0) description.append(" <- ");
            description.append(current.getClass().getName());
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                description.append(" [SQLState ").append(sql.getSQLState()).append(']');
            }
            StackTraceElement[] trace = current.getStackTrace();
            if (trace.length > 0) {
                description.append(" at ").append(trace[0]);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return description.toString();
    }
}
