package com.project.back.domain.quote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuoteCustomer {

    @Column(name = "customer_company_name", length = 100)
    private String companyName;

    @Column(name = "customer_contact_name", length = 50)
    private String contactName;

    @Column(name = "customer_email", length = 100)
    private String email;

    @Column(name = "customer_phone", length = 30)
    private String phone;

    @Column(name = "customer_address", length = 255)
    private String address;
}
