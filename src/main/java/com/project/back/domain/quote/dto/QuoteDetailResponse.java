package com.project.back.domain.quote.dto;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.entity.QuoteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuoteDetailResponse(
        String quoteNumber,
        QuoteStatus status,
        LocalDate issuedDate,
        LocalDate validUntil,
        String deliveryTerm,
        CustomerDetail customer,
        CompanyDetail company,
        List<ItemDetail> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String internalMemo
) {
    public record CustomerDetail(
            String companyName,
            String contactName,
            String email,
            String phone,
            String address
    ) {}

    public record CompanyDetail(
            String name,
            String address,
            String phone,
            String email,
            String businessNumber
    ) {}

    public record ItemDetail(
            int sortOrder,
            String productName,
            String spec,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal lineTotal
    ) {}

    public static QuoteDetailResponse from(Quote quote) {
        return new QuoteDetailResponse(
                quote.getQuoteNumber(),
                quote.getStatus(),
                quote.getIssuedDate(),
                quote.getValidUntil(),
                quote.getDeliveryTerm(),
                new CustomerDetail(
                        quote.getCustomer().getCompanyName(),
                        quote.getCustomer().getContactName(),
                        quote.getCustomer().getEmail(),
                        quote.getCustomer().getPhone(),
                        quote.getCustomer().getAddress()
                ),
                new CompanyDetail(
                        quote.getCompany().getName(),
                        quote.getCompany().getAddress(),
                        quote.getCompany().getPhone(),
                        quote.getCompany().getEmail(),
                        quote.getCompany().getBusinessNumber()
                ),
                quote.getItems().stream().map(QuoteDetailResponse::toItemDetail).toList(),
                quote.getSubtotal(),
                quote.getDiscountAmount(),
                quote.getTaxAmount(),
                quote.getTotalAmount(),
                quote.getInternalMemo()
        );
    }

    private static ItemDetail toItemDetail(QuoteItem item) {
        return new ItemDetail(
                item.getSortOrder(),
                item.getProductName(),
                item.getSpec(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountRate(),
                item.getLineTotal()
        );
    }
}
