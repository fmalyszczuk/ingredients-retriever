package com.malyszczuk.ingredients_retriever.extraction.file;

public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
