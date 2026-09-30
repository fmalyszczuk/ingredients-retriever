package com.malyszczuk.ingredients_retriever.extraction.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileKindTest {

    @Test
    void detect_mapsSupportedExtensionsCaseInsensitively() {
        assertEquals(FileKind.PDF, FileKind.detect("Recipe.PDF"));
        assertEquals(FileKind.DOCX, FileKind.detect("a.docx"));
        assertEquals(FileKind.TEXT, FileKind.detect("notes.md"));
        assertEquals(FileKind.IMAGE, FileKind.detect("shot.JPeG"));
    }

    @Test
    void detect_rejectsUnsupportedOrMissingExtensions() {
        assertThrows(UnsupportedFileTypeException.class, () -> FileKind.detect("old.doc"));
        assertThrows(UnsupportedFileTypeException.class, () -> FileKind.detect("archive.zip"));
        assertThrows(UnsupportedFileTypeException.class, () -> FileKind.detect("noextension"));
    }
}
