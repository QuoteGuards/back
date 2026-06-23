package com.project.back.domain.customer.dto.response;

import com.project.back.domain.customer.entity.Customer;

public record CustomerSearchResponse(
        Long id,
        String companyName,
        String contactName,
        String phone,
        String email
) {
    public static CustomerSearchResponse from(Customer customer) {
        return new CustomerSearchResponse(
                customer.getId(),
                customer.getCompanyName(),
                customer.getContactName(),
                customer.getPhone(),
                customer.getEmail()
        );
    }
}
