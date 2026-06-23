package com.project.back.domain.customer.dto.response;

import com.project.back.domain.customer.entity.Customer;

public record CustomerDetailResponse(
        Long id,
        String companyName,
        String contactName,
        String email,
        String phone,
        String address,
        String businessNumber,
        String memo
) {
    public static CustomerDetailResponse from(Customer customer) {
        return new CustomerDetailResponse(
                customer.getId(),
                customer.getCompanyName(),
                customer.getContactName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getBusinessNumber(),
                customer.getMemo()
        );
    }
}
