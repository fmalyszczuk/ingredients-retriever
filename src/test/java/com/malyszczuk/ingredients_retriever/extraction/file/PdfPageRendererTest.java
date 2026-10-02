package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfPageRendererTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private final PdfPageRenderer renderer = new PdfPageRenderer();

    @Test
    void render_returnsOnePngPerPage_cappedAtMaxPages() throws IOException {
        byte[] pdf = pdfWithPages(5);

        List<byte[]> images = renderer.render(pdf, 3);

        assertEquals(3, images.size());
        for (byte[] image : images) {
            assertEquals(PNG_SIGNATURE[0], image[0]);
            assertEquals("PNG", new String(image, 1, 3, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void render_returnsAllPages_whenFewerThanMax() throws IOException {
        assertEquals(2, renderer.render(pdfWithPages(2), 3).size());
    }

    @Test
    void render_throws_whenContentIsNotAPdf() {
        assertThrows(RecipeExtractionException.class,
                () -> renderer.render("not a pdf".getBytes(StandardCharsets.UTF_8), 3));
    }

    private byte[] pdfWithPages(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
