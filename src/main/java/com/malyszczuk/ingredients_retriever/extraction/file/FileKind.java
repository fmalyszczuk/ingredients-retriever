package com.malyszczuk.ingredients_retriever.extraction.file;

import java.util.Locale;
import java.util.Map;

public enum FileKind {
    PDF, DOCX, TEXT, IMAGE;

    private static final Map<String, FileKind> BY_EXTENSION = Map.of(
            "pdf", PDF,
            "docx", DOCX,
            "txt", TEXT,
            "md", TEXT,
            "png", IMAGE,
            "jpg", IMAGE,
            "jpeg", IMAGE
    );

    public static final String SUPPORTED_EXTENSIONS = "pdf, docx, txt, md, png, jpg, jpeg";

    public static FileKind detect(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        FileKind kind = BY_EXTENSION.get(extension);
        if (kind == null) {
            throw new UnsupportedFileTypeException(
                    "Unsupported file type for '" + filename + "'. Supported: " + SUPPORTED_EXTENSIONS);
        }
        return kind;
    }
}
