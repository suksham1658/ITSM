package com.nbfc.itsm.exception;

import com.nbfc.itsm.audit.AuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditRecorder auditRecorder;

    public GlobalExceptionHandler(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @ExceptionHandler(ItsmException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusiness(ItsmException ex, Model model, HttpServletRequest request) {
        log.warn("Business error {} on {}", ex.getCode(), request.getRequestURI());
        model.addAttribute("status", 400);
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleDenied(AccessDeniedException ex, Model model, HttpServletRequest request) {
        model.addAttribute("status", 403);
        model.addAttribute("message", "You do not have permission to view this page.");
        return "error";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleMissing(NoHandlerFoundException ex, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("message", "Page not found.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleOther(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        auditRecorder.record("SYSTEM", "UNHANDLED_ERROR", request.getRequestURI(), "FAILURE");
        model.addAttribute("status", 500);
        model.addAttribute("message", "An unexpected error occurred. The event has been logged.");
        return "error";
    }
}
