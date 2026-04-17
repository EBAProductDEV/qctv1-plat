package com.qctv1.iam.common;

import com.qctv1.iam.user.controller.InternalUserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InternalUserController.class)
public class InternalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalApiExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UNKNOWN_REQUEST_ID = "-";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final String VALIDATION_ERROR_MESSAGE = "Request validation failed";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn(
                "iam internal business exception requestId={} method={} path={} code={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex.getCode(),
                ex.getMessage()
        );
        return buildResponse(HttpStatus.valueOf(ex.getCode()), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception ex, HttpServletRequest request) {
        log.warn(
                "iam internal validation exception requestId={} method={} path={} exception={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, HttpServletRequest request) {
        log.error(
                "iam internal unexpected exception requestId={} method={} path={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex
        );
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? UNKNOWN_REQUEST_ID : requestId;
    }
}
