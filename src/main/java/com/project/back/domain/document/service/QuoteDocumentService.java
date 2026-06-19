package com.project.back.domain.document.service;

import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.dto.QuotePdfRequest;
import com.project.back.domain.document.pdf.QuotePdfGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuoteDocumentService {

    private final QuotePdfGenerator pdfGenerator;

    public QuotePdfData.DocumentResult generatePdf(QuotePdfRequest req) throws IOException {
        QuotePdfData.QuoteInfo quoteInfo = toQuoteInfo(req);
        byte[] content = pdfGenerator.generate(quoteInfo);

        String fileName = "견적서_" + req.quoteNumber() + ".pdf";
        return new QuotePdfData.DocumentResult(
                fileName,
                content,
                "application/pdf",
                content.length,
                LocalDateTime.now()
        );
    }

    private QuotePdfData.QuoteInfo toQuoteInfo(QuotePdfRequest req) {
        QuotePdfData.CustomerInfo customer = new QuotePdfData.CustomerInfo(
                req.customer().companyName(),
                req.customer().contactName(),
                req.customer().email(),
                req.customer().phone(),
                req.customer().address()
        );

        QuotePdfData.CompanyInfo company = new QuotePdfData.CompanyInfo(
                req.company().name(),
                req.company().address(),
                req.company().phone(),
                req.company().email(),
                req.company().businessNumber()
        );

        List<QuotePdfData.QuoteItem> items = req.items().stream()
                .map(i -> new QuotePdfData.QuoteItem(
                        i.sortOrder(),
                        i.productName(),
                        i.spec(),
                        i.quantity(),
                        i.unitPrice(),
                        i.discountRate(),
                        i.lineTotal()
                ))
                .toList();

        return new QuotePdfData.QuoteInfo(
                req.quoteNumber(),
                req.issuedDate(),
                req.validUntil(),
                req.deliveryTerm(),
                customer,
                company,
                items,
                req.subtotal(),
                req.discountAmount(),
                req.taxAmount(),
                req.totalAmount(),
                req.internalMemo()
        );
    }
}
