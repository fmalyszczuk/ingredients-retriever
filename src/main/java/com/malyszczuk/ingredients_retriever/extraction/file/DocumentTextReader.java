package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Pulls the plain text out of PDF, DOCX and text files. Images are not handled here. */
@Component
public class DocumentTextReader {

    public String read(FileKind kind, String filename, byte[] content) {
        if (kind == FileKind.IMAGE) {
            throw new IllegalArgumentException("Images have no text layer to read");
        }

        try {
            return switch (kind) {
                case PDF -> readPdf(content);
                case DOCX -> readDocx(content);
                default -> new String(content, StandardCharsets.UTF_8);
            };
        } catch (IOException | RuntimeException e) {
            throw new RecipeExtractionException("Could not read '" + filename + "'; is it a valid " + kind + " file?", e);
        }
    }

    private String readPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String readDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
