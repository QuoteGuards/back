package com.project.back.domain.document.service;

import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.dto.QuotePdfRequest;
import com.project.back.domain.document.pdf.QuotePdfGenerator;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuoteDocumentService {

    private final QuotePdfGenerator pdfGenerator;

    public QuotePdfData.DocumentResult generatePdf(QuotePdfRequest req) throws IOException {
        QuotePdfData.QuoteInfo quoteInfo = toQuoteInfo(req);
        return buildResult(quoteInfo);
    }

    /** Quote 엔티티로부터 PDF 생성 (이메일 첨부 등 서버 측 발행용) */
    public QuotePdfData.DocumentResult generatePdf(Quote quote) throws IOException {
        return buildResult(toQuoteInfo(quote));
    }

    private QuotePdfData.DocumentResult buildResult(QuotePdfData.QuoteInfo quoteInfo) throws IOException {
        byte[] content = pdfGenerator.generate(quoteInfo);
        String fileName = "견적서_" + quoteInfo.quoteNumber() + ".pdf";
        return new QuotePdfData.DocumentResult(
                fileName,
                content,
                "application/pdf",
                content.length,
                LocalDateTime.now()
        );
    }

    private QuotePdfData.QuoteInfo toQuoteInfo(Quote quote) {
        QuotePdfData.CustomerInfo customer = new QuotePdfData.CustomerInfo(
                quote.getQuoteCustomer().getCompanyName(),
                quote.getQuoteCustomer().getContactName(),
                quote.getQuoteCustomer().getEmail(),
                quote.getQuoteCustomer().getPhone(),
                quote.getQuoteCustomer().getAddress()
        );

        QuotePdfData.CompanyInfo company = new QuotePdfData.CompanyInfo(
                quote.getCompany().getName(),
                quote.getCompany().getAddress(),
                quote.getCompany().getPhone(),
                quote.getCompany().getEmail(),
                quote.getCompany().getBusinessNumber()
        );

        List<QuotePdfData.QuoteItem> items = quote.getItems().stream()
                .map(this::toPdfItem)
                .toList();

        return new QuotePdfData.QuoteInfo(
                quote.getQuoteNumber(),
                quote.getIssuedDate(),
                quote.getValidUntil(),
                quote.getDeliveryTerm(),
                customer,
                company,
                items,
                quote.getSubtotal(),
                quote.getDiscountAmount(),
                quote.getTaxAmount(),
                quote.getTotalAmount(),
                quote.getInternalMemo()
        );
    }

    private QuotePdfData.QuoteItem toPdfItem(QuoteItem item) {
        return new QuotePdfData.QuoteItem(
                item.getSortOrder() != null ? item.getSortOrder() : 0,
                item.getProductName(),
                item.getSpec(),
                item.getQuantity() != null ? item.getQuantity().intValue() : 0,
                item.getUnitPrice(),
                item.getDiscountRate() != null ? item.getDiscountRate() : BigDecimal.ZERO,
                item.getLineTotal()
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
