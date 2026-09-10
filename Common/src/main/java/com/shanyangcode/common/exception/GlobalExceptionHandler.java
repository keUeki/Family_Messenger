package com.shanyangcode.common.exception;


import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.common.ResultUtils;
import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("Failed to parse request body: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.INVALID_PARAMETER_ERROR, "Request body is malformed or empty");
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "System error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
        });
        String message = errors.toString();
        return ResultUtils.error(ErrorCode.INVALID_PARAMETER_ERROR, message);
    }


    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).findFirst().orElse("Request parameter validation failed");
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public BaseResponse<?> handlerMissingServletRequestParameterException(Exception e) {
        log.error("Missing required parameter: {}", e.toString());
        return ResultUtils.error(ErrorCode.INVALID_PARAMETER_ERROR, "Missing required parameter");
    }

    @ExceptionHandler(InputGuardrailException.class)
    public BaseResponse<?> inputGuardrailExceptionHandler(InputGuardrailException e) {
        log.error("Sensitive word blocked: {}", e.getMessage());
        // Return the message carried by the exception straight to the client
        // Alternatively, always return SENSITIVE_WORD_ERROR
        return ResultUtils.error(ErrorCode.SENSITIVE_WORD_ERROR);
    }
}

