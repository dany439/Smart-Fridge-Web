package com.smartfridge.exceptions;

// 1. For when the item doesn't exist
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(String message) { super(message); }
}
