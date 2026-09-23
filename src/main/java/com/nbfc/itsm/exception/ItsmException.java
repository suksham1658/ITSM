package com.nbfc.itsm.exception;

public class ItsmException extends RuntimeException {

    private final String code;

    public ItsmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
