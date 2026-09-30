package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTextReaderTest {

    private final DocumentTextReader reader = new DocumentTextReader();

    @Test
    void read_extractsTextFromPdf() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("2 cups flour");
                stream.endText();
            }
            document.save(out);
            pdf = out.toByteArray();
        }

        assertTrue(reader.read(FileKind.PDF, "r.pdf", pdf).contains("2 cups flour"));
    }

    @Test
    void read_extractsTextFromDocx() throws IOException {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("3 eggs");
            document.write(out);
            docx = out.toByteArray();
        }

        assertTrue(reader.read(FileKind.DOCX, "r.docx", docx).contains("3 eggs"));
    }

    @Test
    void read_decodesPlainText() {
        assertEquals("200 g sugar", reader.read(FileKind.TEXT, "r.txt", "200 g sugar".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void read_throwsExtractionException_whenContentDoesNotMatchTheFileType() {
        assertThrows(RecipeExtractionException.class,
                () -> reader.read(FileKind.PDF, "fake.pdf", "definitely not a pdf".getBytes(StandardCharsets.UTF_8)));
        assertThrows(RecipeExtractionException.class,
                () -> reader.read(FileKind.DOCX, "fake.docx", "definitely not a docx".getBytes(StandardCharsets.UTF_8)));
    }
}
