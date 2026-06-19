package com.project.back.domain.document.pdf;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;

import com.project.back.domain.document.dto.QuotePdfData;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class QuotePdfGenerator {

    private static final DeviceRgb COLOR_PRIMARY       = new DeviceRgb(0x18, 0x5F, 0xA5);
    private static final DeviceRgb COLOR_PRIMARY_LIGHT = new DeviceRgb(0xE6, 0xF1, 0xFB);
    private static final DeviceRgb COLOR_PRIMARY_DARK  = new DeviceRgb(0x04, 0x2C, 0x53);
    private static final DeviceRgb COLOR_BORDER        = new DeviceRgb(0xD3, 0xD1, 0xC7);
    private static final DeviceRgb COLOR_ROW_EVEN      = new DeviceRgb(0xF9, 0xF8, 0xF5);
    private static final DeviceRgb COLOR_TEXT_MUTED    = new DeviceRgb(0x88, 0x87, 0x80);
    private static final DeviceRgb COLOR_TEXT_BODY     = new DeviceRgb(0x2C, 0x2C, 0x2A);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    public byte[] generate(QuotePdfData.QuoteInfo quote) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc  = new PdfDocument(new PdfWriter(baos));
            Document    document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 50, 40, 50);

            PdfFont regular = loadFont("fonts/NotoSansKR-Regular.ttf");
            PdfFont medium  = loadFont("fonts/NotoSansKR-Medium.ttf");
            PdfFont bold    = loadFont("fonts/NotoSansKR-Bold.ttf");

            addHeader(document, quote, regular, medium, bold);
            addDivider(document);
            addMetaInfo(document, quote, regular, medium);
            addItemTable(document, quote, regular, medium, bold);
            addTotalSection(document, quote, regular, medium, bold);
            addFooter(document, quote, regular);

            document.close();
            return baos.toByteArray();
        }
    }

    private void addHeader(Document doc, QuotePdfData.QuoteInfo quote,
                           PdfFont regular, PdfFont medium, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER).setMarginBottom(4);

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        left.add(new Paragraph(quote.company().name())
                .setFont(bold).setFontSize(16).setFontColor(COLOR_PRIMARY_DARK).setMarginBottom(4));
        left.add(new Paragraph(quote.company().address())
                .setFont(regular).setFontSize(9).setFontColor(COLOR_TEXT_MUTED).setMarginBottom(1));
        left.add(new Paragraph(quote.company().phone() + "  |  " + quote.company().email())
                .setFont(regular).setFontSize(9).setFontColor(COLOR_TEXT_MUTED).setMarginBottom(1));
        left.add(new Paragraph("사업자등록번호: " + quote.company().businessNumber())
                .setFont(regular).setFontSize(9).setFontColor(COLOR_TEXT_MUTED));

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                .setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("견  적  서")
                .setFont(bold).setFontSize(22).setFontColor(COLOR_PRIMARY_DARK)
                .setCharacterSpacing(6).setMarginBottom(6));
        right.add(new Paragraph(quote.quoteNumber())
                .setFont(medium).setFontSize(10).setFontColor(COLOR_PRIMARY).setMarginBottom(2));
        right.add(new Paragraph("발행일: " + quote.issuedDate().format(DATE_FMT))
                .setFont(regular).setFontSize(9).setFontColor(COLOR_TEXT_MUTED));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);
    }

    private void addDivider(Document doc) {
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(2f))
                .setStrokeColor(COLOR_PRIMARY).setMarginTop(8).setMarginBottom(16));
    }

    private void addMetaInfo(Document doc, QuotePdfData.QuoteInfo quote,
                             PdfFont regular, PdfFont medium) {
        Table meta = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER).setMarginBottom(20);

        Cell leftCell = new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(COLOR_PRIMARY_LIGHT)
                .setPadding(12).setBorderRadius(new BorderRadius(4));
        leftCell.add(metaLabel("수  신", regular));
        leftCell.add(metaValue(quote.customer().companyName(), medium));
        leftCell.add(new Paragraph(" ").setFontSize(4));
        leftCell.add(metaLabel("담당자", regular));
        leftCell.add(metaValue(quote.customer().contactName(), medium));
        leftCell.add(new Paragraph(" ").setFontSize(4));
        leftCell.add(metaLabel("연락처", regular));
        leftCell.add(metaValue(quote.customer().phone(), medium));

        Cell rightCell = new Cell().setBorder(Border.NO_BORDER)
                .setPadding(12).setPaddingLeft(24);
        rightCell.add(metaLabel("유효기간", regular));
        rightCell.add(metaValue(quote.validUntil().format(DATE_FMT) + "까지", medium));
        rightCell.add(new Paragraph(" ").setFontSize(4));
        rightCell.add(metaLabel("납  기", regular));
        rightCell.add(metaValue(quote.deliveryTerm(), medium));
        rightCell.add(new Paragraph(" ").setFontSize(4));
        rightCell.add(metaLabel("결제조건", regular));
        rightCell.add(metaValue("계좌이체", medium));

        meta.addCell(leftCell);
        meta.addCell(rightCell);
        doc.add(meta);
    }

    private void addItemTable(
            Document doc,
            QuotePdfData.QuoteInfo quote,
            PdfFont regular,
            PdfFont medium,
            PdfFont bold
    ) {

        float[] colWidths = {6, 30, 14, 8, 18, 8, 16};
        Table table = new Table(UnitValue.createPercentArray(colWidths))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(12);

        for (String h : new String[]{"No.", "품목명", "규격", "수량", "단가 (원)", "할인", "금액 (원)"}) {
            table.addHeaderCell(
                    new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)
                                    .setFontColor(COLOR_PRIMARY_DARK))
                            .setBackgroundColor(COLOR_PRIMARY_LIGHT)
                            .setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                            .setTextAlignment(TextAlignment.CENTER)
                            .setPaddingTop(7).setPaddingBottom(7));
        }

        List<QuotePdfData.QuoteItem> items = quote.items();
        for (int i = 0; i < items.size(); i++) {
            QuotePdfData.QuoteItem item = items.get(i);
            DeviceRgb rowBg = (i % 2 == 1) ? COLOR_ROW_EVEN : new DeviceRgb(0xFF, 0xFF, 0xFF);

            table.addCell(itemCell(String.valueOf(item.sortOrder()), regular, 9, rowBg, TextAlignment.CENTER));
            table.addCell(itemCell(item.productName(), medium, 9, rowBg, TextAlignment.LEFT));
            table.addCell(itemCell(item.spec(), regular, 8, rowBg, TextAlignment.CENTER));
            table.addCell(itemCell(String.valueOf(item.quantity()), regular, 9, rowBg, TextAlignment.CENTER));
            table.addCell(itemCell(formatMoney(item.unitPrice()), regular, 9, rowBg, TextAlignment.RIGHT));
            table.addCell(itemCell(
                    item.discountRate().compareTo(BigDecimal.ZERO) > 0
                            ? item.discountRate().stripTrailingZeros().toPlainString() + "%" : "-",
                    regular, 9, rowBg, TextAlignment.CENTER));
            table.addCell(itemCell(formatMoney(item.lineTotal()), medium, 9, rowBg, TextAlignment.RIGHT));
        }
        doc.add(table);
    }

    private void addTotalSection(
            Document doc,
            QuotePdfData.QuoteInfo quote,
            PdfFont regular,
            PdfFont medium,
            PdfFont bold
    ) {

        Table wrapper = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER).setMarginBottom(24);

        Cell noteCell = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(16);

        if (quote.internalMemo() != null && !quote.internalMemo().isBlank()) {
            noteCell.add(new Paragraph("비고").setFont(medium).setFontSize(9)
                    .setFontColor(COLOR_TEXT_MUTED).setMarginBottom(4));
            noteCell.add(new Paragraph(quote.internalMemo()).setFont(regular)
                    .setFontSize(9).setFontColor(COLOR_TEXT_BODY));
        }

        Table totalTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100));

        addTotalRow(totalTable, "공급가액", formatMoneyWon(quote.subtotal()), regular, medium);

        if (quote.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totalTable, "할인금액", "- " + formatMoneyWon(quote.discountAmount()), regular, medium);
        }

        addTotalRow(totalTable, "부가세 (10%)", formatMoneyWon(quote.taxAmount()), regular, medium);
        totalTable.addCell(new Cell(1, 2).add(new Paragraph(" ").setFontSize(2)).setBorder(Border.NO_BORDER));

        totalTable.addCell(new Cell()
                .add(new Paragraph("최종 합계금액").setFont(bold).setFontSize(11).setFontColor(COLOR_PRIMARY_DARK))
                .setBackgroundColor(COLOR_PRIMARY_LIGHT)
                .setBorderTop(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setBorderBottom(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setBorderLeft(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setBorderRight(Border.NO_BORDER)
                .setPaddingTop(9).setPaddingBottom(9).setPaddingLeft(10));

        totalTable.addCell(new Cell()
                .add(new Paragraph(formatMoneyWon(quote.totalAmount()))
                        .setFont(bold).setFontSize(13).setFontColor(COLOR_PRIMARY_DARK)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(COLOR_PRIMARY_LIGHT)
                .setBorderTop(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setBorderBottom(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(new SolidBorder(COLOR_PRIMARY, 1.5f))
                .setPaddingTop(9).setPaddingBottom(9).setPaddingRight(10));

        Cell totalCell = new Cell().setBorder(Border.NO_BORDER);
        totalCell.add(totalTable);
        wrapper.addCell(noteCell);
        wrapper.addCell(totalCell);
        doc.add(wrapper);
    }

    private void addFooter(
            Document doc,
            QuotePdfData.QuoteInfo quote,
            PdfFont regular
    ) {

        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.DashedLine(0.5f))
                .setStrokeColor(COLOR_BORDER).setMarginBottom(12));

        doc.add(new Paragraph(
                "위 금액으로 견적합니다. 본 견적서는 " + quote.validUntil().format(DATE_FMT) +
                        "까지 유효하며, 이후에는 가격이 변동될 수 있습니다. " +
                        "문의사항은 " + quote.company().phone() + " 또는 " + quote.company().email() + "으로 연락 주시기 바랍니다.")
                .setFont(regular).setFontSize(8).setFontColor(COLOR_TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private Paragraph metaLabel(String text, PdfFont font) {
        return new Paragraph(text).setFont(font).setFontSize(8)
                .setFontColor(COLOR_TEXT_MUTED).setMarginBottom(1);
    }

    private Paragraph metaValue(String text, PdfFont font) {
        return new Paragraph(text).setFont(font).setFontSize(10)
                .setFontColor(COLOR_TEXT_BODY).setMarginBottom(0);
    }

    private Cell itemCell(
            String text,
            PdfFont font,
            int size,
            DeviceRgb bg,
            TextAlignment align
    ) {

        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(size).setFontColor(COLOR_TEXT_BODY))
                .setBackgroundColor(bg).setBorder(new SolidBorder(COLOR_BORDER, 0.5f))
                .setTextAlignment(align).setPaddingTop(6).setPaddingBottom(6)
                .setPaddingLeft(5).setPaddingRight(5);
    }

    private void addTotalRow(Table table, String label, String value, PdfFont regular, PdfFont medium) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(regular).setFontSize(9).setFontColor(COLOR_TEXT_MUTED))
                .setBorder(Border.NO_BORDER).setPaddingTop(3).setPaddingBottom(3));
        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(medium).setFontSize(9).setFontColor(COLOR_TEXT_BODY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingTop(3).setPaddingBottom(3));
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getInstance(Locale.KOREA).format(amount);
    }

    private String formatMoneyWon(BigDecimal amount) {
        return "₩ " + NumberFormat.getInstance(Locale.KOREA).format(amount);
    }

    private PdfFont loadFont(String path) throws IOException {
        byte[] bytes = new ClassPathResource(path).getContentAsByteArray();
        return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H);
    }
}