package com.example.banking.exception;


public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found with identifier: " + identifier);
    }
}
