package com.project.back.domain.quote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuoteCompany {

    @Column(name = "company_name", length = 100)
    private String name;

    @Column(name = "company_address", length = 255)
    private String address;

    @Column(name = "company_phone", length = 30)
    private String phone;

    @Column(name = "company_email", length = 100)
    private String email;

    @Column(name = "company_business_number", length = 20)
    private String businessNumber;
}
