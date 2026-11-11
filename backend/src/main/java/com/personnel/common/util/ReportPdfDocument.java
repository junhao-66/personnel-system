package com.personnel.common.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Text-based A4 pagination, with embedded subset font and no screenshot rasterization. */
public final class ReportPdfDocument implements AutoCloseable {

    private static final float MARGIN = 48,
        WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
    private final PDDocument document = new PDDocument();
    private final PDType0Font font;
    private PDPageContentStream stream;
    private float y;

    public ReportPdfDocument(Path fontPath) throws IOException {
        font = PDType0Font.load(document, fontPath.toFile());
        document.getDocumentInformation().setTitle("人格偏好报告 - 当次保存快照");
        document.getDocumentInformation().setAuthor("企业员工人格测评与人才分析系统");
        page();
    }

    private String supported(String value) throws IOException {
        var result = new StringBuilder();
        for (int code : (value == null ? "" : value).codePoints().toArray()) {
            if (Character.isISOControl(code)) {
                if (code == '\t') result.append(' ');
                continue;
            }
            String character = new String(Character.toChars(code));
            try {
                font.encode(character);
                result.append(character);
            } catch (IllegalArgumentException unsupported) {
                result.append('?');
            }
        }
        return result.toString();
    }

    private void text(String value, float size, float x, float baseline, Color color)
        throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(value);
        stream.endText();
    }

    private void page() throws IOException {
        if (stream != null) stream.close();
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        text(
            "人格偏好报告 / 当次保存快照",
            9,
            MARGIN,
            PDRectangle.A4.getHeight() - 30,
            new Color(90, 104, 123)
        );
        y = PDRectangle.A4.getHeight() - 62;
    }

    private void ensure(float height) throws IOException {
        if (y - height < 52) page();
    }

    private void paragraph(String value, float size, float leading, Color color)
        throws IOException {
        for (String segment : (value == null ? "" : value).split("\\R", -1)) {
            String cleaned = supported(segment);
            var line = new StringBuilder();
            for (int code : cleaned.codePoints().toArray()) {
                String character = new String(Character.toChars(code));
                if (
                    !line.isEmpty() && (font.getStringWidth(line + character) * size) / 1000 > WIDTH
                ) {
                    ensure(leading);
                    text(line.toString(), size, MARGIN, y, color);
                    y -= leading;
                    line.setLength(0);
                }
                line.append(character);
            }
            ensure(leading);
            text(line.toString(), size, MARGIN, y, color);
            y -= leading;
        }
        y -= 5;
    }

    public void title(String value) throws IOException {
        paragraph(value, 22, 31, new Color(0, 40, 100));
    }

    public void heading(String value) throws IOException {
        ensure(62);
        y -= 9;
        paragraph(value, 13, 21, new Color(0, 40, 100));
    }

    public void body(String value) throws IOException {
        paragraph(value, 10.5f, 17, new Color(40, 48, 60));
    }

    public void note(String value) throws IOException {
        paragraph(value, 9, 14, new Color(80, 94, 110));
    }

    public void dimension(String first, String second, float percentage, String explanation)
        throws IOException {
        ensure(75);
        String firstLabel = supported(first),
            secondLabel = supported(second);
        text(firstLabel, 10.5f, MARGIN, y, new Color(40, 48, 60));
        text(
            secondLabel,
            10.5f,
            MARGIN + WIDTH - (font.getStringWidth(secondLabel) * 10.5f) / 1000,
            y,
            new Color(40, 48, 60)
        );
        y -= 22;
        float left = Math.max(0, Math.min(100, percentage));
        stream.setNonStrokingColor(new Color(217, 228, 237));
        stream.addRect(MARGIN, y - 3, WIDTH, 7);
        stream.fill();
        stream.setNonStrokingColor(new Color(32, 88, 154));
        stream.addRect(MARGIN, y - 3, (WIDTH * left) / 100, 7);
        stream.fill();
        y -= 19;
        note(explanation);
    }

    public byte[] finish() throws IOException {
        stream.close();
        stream = null;
        int count = document.getNumberOfPages();
        for (int index = 0; index < count; index++) {
            try (
                var footer = new PDPageContentStream(
                    document,
                    document.getPage(index),
                    PDPageContentStream.AppendMode.APPEND,
                    true
                );
            ) {
                footer.beginText();
                footer.setFont(font, 8);
                footer.setNonStrokingColor(new Color(95, 105, 120));
                footer.newLineAtOffset(MARGIN, 28);
                footer.showText(
                    "个人报告，请妥善保管。仅表示偏好，不代表能力或绩效。     第 " +
                    (index + 1) +
                    " / " +
                    count +
                    " 页"
                );
                footer.endText();
            }
        }
        var output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (stream != null) stream.close();
        document.close();
    }
}
