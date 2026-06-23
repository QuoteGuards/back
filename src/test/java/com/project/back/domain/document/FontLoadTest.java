package com.project.back.domain.document;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.pdf.QuotePdfGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontLoadTest {

    @Test
    void notoSansKrFontsLoad() throws Exception {
        for (String path : new String[]{
                "fonts/NotoSansKR-Regular.ttf",
                "fonts/NotoSansKR-Medium.ttf",
                "fonts/NotoSansKR-Bold.ttf"
        }) {
            byte[] bytes = new ClassPathResource(path).getContentAsByteArray();
            PdfFont font = PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H);
            assertNotNull(font, path + " 로드 실패");
        }
    }

    @Test
    void generatesQuotePdf() throws Exception {
        var customer = new QuotePdfData.CustomerInfo("ABC상사", "홍길동", "abc@test.com", "010-1234-5678", "서울시");
        var company = new QuotePdfData.CompanyInfo("QuoteGuard", "서울시", "02-123-4567", "sales@qg.com", "123-45-67890");
        var item = new QuotePdfData.QuoteItem(0, "노트북", "15인치", 2, new BigDecimal("1500000"), BigDecimal.ZERO, new BigDecimal("3000000"));

        var quote = new QuotePdfData.QuoteInfo(
                "Q-2026-0001", LocalDate.now(), LocalDate.now().plusMonths(1), "협의",
                customer, company, List.of(item),
                new BigDecimal("3000000"), BigDecimal.ZERO, new BigDecimal("300000"), new BigDecimal("3300000"),
                "테스트 메모");

        byte[] pdf = new QuotePdfGenerator().generate(quote);
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "PDF가 비정상적으로 작음");
    }
}
