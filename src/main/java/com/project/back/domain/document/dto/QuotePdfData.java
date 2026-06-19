package com.project.back.domain.document.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class QuotePdfData {

    public record QuoteInfo(
            String quoteNumber,
            LocalDate issuedDate,
            LocalDate validUntil,
            String deliveryTerm,
            CustomerInfo customer,
            CompanyInfo company,
            List<QuoteItem> items,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String internalMemo
    ) {}

    public record CustomerInfo(
            String companyName,
            String contactName,
            String email,
            String phone,
            String address
    ) {}

    public record CompanyInfo(
            String name,
            String address,
            String phone,
            String email,
            String businessNumber
    ) {}

    public record QuoteItem(
            int sortOrder,
            String productName,
            String spec,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal lineTotal
    ) {}

    public record DocumentResult(
            String fileName,
            byte[] content,
            String contentType,
            long fileSize,
            LocalDateTime generatedAt
    ) {}
}