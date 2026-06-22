package com.project.back.domain.customer.controller;

import com.project.back.domain.customer.dto.request.CustomerCreateRequest;
import com.project.back.domain.customer.dto.response.CustomerDetailResponse;
import com.project.back.domain.customer.dto.response.CustomerSearchResponse;
import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.service.CustomerService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final UserRepository userRepository;

    //고객 검색 (견적 작성 화면 자동완성용)
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CustomerSearchResponse>>> searchCustomers(
            @AuthenticationPrincipal Long userId,
            @RequestParam String name) {

        List<CustomerSearchResponse> result = customerService
                .searchByCompanyName(userId, name)
                .stream()
                .map(CustomerSearchResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    //고객 상세 조회
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomerDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long customerId) {

        Customer customer = customerService.getCustomer(customerId, userId);
        return ResponseEntity.ok(ApiResponse.success(CustomerDetailResponse.from(customer)));
    }

    //신규 고객 등록
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> createCustomer(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CustomerCreateRequest request) {

        User user = getUser(userId);
        Customer customer = customerService.createCustomer(
                user,
                request.companyName(),
                request.contactName(),
                request.email(),
                request.phone(),
                request.businessNumber(),
                request.address(),
                request.memo()
        );
        return ResponseEntity.ok(ApiResponse.success("고객이 등록되었습니다.", CustomerDetailResponse.from(customer)));
    }

    //고객 정보 수정
    @PutMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> updateCustomer(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long customerId,
            @RequestBody @Valid CustomerCreateRequest request) {

        Customer customer = customerService.updateCustomer(
                customerId,
                userId,
                request.companyName(),
                request.contactName(),
                request.email(),
                request.phone(),
                request.businessNumber(),
                request.address(),
                request.memo()
        );
        return ResponseEntity.ok(ApiResponse.success("고객 정보가 수정되었습니다.", CustomerDetailResponse.from(customer)));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}