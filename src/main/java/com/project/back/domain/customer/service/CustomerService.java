package com.project.back.domain.customer.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    // 회사명으로 고객 검색 (견적 작성 화면 자동완성용)
    public List<Customer> searchByCompanyName(Long userId, String companyName) {
        return customerRepository.findByCreatedByIdAndCompanyNameContainingIgnoreCase(userId, companyName);
    }

    // 내가 등록한 고객 전체 목록
    public List<Customer> getMyCustomers(Long userId) {
        return customerRepository.findByCreatedById(userId);
    }

    // 고객 단건 조회
    public Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 고객입니다. id=" + customerId));
    }

    // 견적 작성 시 신규 고객 등록
    @Transactional
    public Customer createCustomer(User createdBy, String companyName, String contactName,
                                   String email, String phone, String businessNumber,
                                   String address, String memo) {
        Customer customer = Customer.builder()
                .createdBy(createdBy)
                .companyName(companyName)
                .contactName(contactName)
                .email(email)
                .phone(phone)
                .businessNumber(businessNumber)
                .address(address)
                .memo(memo)
                .build();
        return customerRepository.save(customer);
    }

    // 고객 정보 수정
    @Transactional
    public Customer updateCustomer(Long customerId, String companyName, String contactName,
                                   String email, String phone, String businessNumber,
                                   String address, String memo) {
        Customer customer = getCustomer(customerId);
        customer.update(companyName, contactName, email, phone, businessNumber, address, memo);
        return customer;
    }
}
