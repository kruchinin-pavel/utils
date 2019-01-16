package org.kpa.util;

/**
 * Created with IntelliJ IDEA.
 * User: kruchinin
 * Date: 08.07.12
 * Time: 22:26
 * To change this template use File | Settings | File Templates.
 */
public class ValidationException extends Exception {
    public ValidationException() {
    }

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }

    public ValidationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
