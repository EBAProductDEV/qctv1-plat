package com.qctv1.iam.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UNKNOWN_REQUEST_ID = "-";
    private static final String INTERNAL_ERROR_MESSAGE = "系统异常，请稍后重试";
    private static final String VALIDATION_ERROR_MESSAGE = "请求参数不合法";

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn(
                "iam business exception requestId={} method={} path={} code={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex.getCode(),
                ex.getMessage()
        );
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ApiResponse<Void> handleValidation(Exception ex, HttpServletRequest request) {
        log.warn(
                "iam validation exception requestId={} method={} path={} exception={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage()
        );
        return ApiResponse.fail(400, VALIDATION_ERROR_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error(
                "iam unexpected exception requestId={} method={} path={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                ex
        );
        return ApiResponse.fail(500, INTERNAL_ERROR_MESSAGE);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? UNKNOWN_REQUEST_ID : requestId;
    }
}
