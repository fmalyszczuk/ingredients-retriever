package com.malyszczuk.ingredients_retriever.extraction.file;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Renders PDF pages to PNG images, for PDFs without a text layer (scans) that a vision model has to read. */
@Component
public class PdfPageRenderer {

    // Enough resolution for a vision model to read print without producing huge images.
    private static final float DPI = 150f;

    public List<byte[]> render(byte[] pdf, int maxPages) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);

            List<byte[]> images = new ArrayList<>();
            for (int page = 0; page < pages; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, DPI);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                images.add(out.toByteArray());
            }
            return images;
        } catch (IOException | RuntimeException e) {
            throw new RecipeExtractionException("Could not render the pages of the PDF", e);
        }
    }
}
