package com.eRez.user.exception;

import com.eRez.common.exception.ServiceException;

public class UserException extends ServiceException {
    public UserException(String message) {
        super(message);
    }
}
