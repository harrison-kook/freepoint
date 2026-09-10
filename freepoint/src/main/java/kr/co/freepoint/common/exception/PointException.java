package kr.co.freepoint.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PointException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public PointException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

}
