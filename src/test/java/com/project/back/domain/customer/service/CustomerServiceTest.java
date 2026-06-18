package com.project.back.domain.customer.service;

import com.project.back.domain.customer.entity.Customer;
import com.project.back.domain.customer.repository.CustomerRepository;
import com.project.back.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService 단위 테스트")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    // ── 고객 검색 ──────────────────────────────────────

    @Nested
    @DisplayName("고객 검색 (searchByCompanyName)")
    class SearchTests {

        @Test
        @DisplayName("회사명으로 검색 시 결과 반환")
        void searchByCompanyName_returnsResults() {
            Customer customer = mock(Customer.class);
            when(customerRepository.findByCreatedByIdAndCompanyNameContainingIgnoreCase(1L, "홍"))
                    .thenReturn(List.of(customer));

            List<Customer> result = customerService.searchByCompanyName(1L, "홍");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("검색 결과 없으면 빈 리스트 반환")
        void searchByCompanyName_returnsEmpty() {
            when(customerRepository.findByCreatedByIdAndCompanyNameContainingIgnoreCase(1L, "없는고객"))
                    .thenReturn(List.of());

            List<Customer> result = customerService.searchByCompanyName(1L, "없는고객");

            assertThat(result).isEmpty();
        }
    }

    // ── 고객 단건 조회 ─────────────────────────────────

    @Nested
    @DisplayName("고객 단건 조회 (getCustomer)")
    class GetCustomerTests {

        @Test
        @DisplayName("존재하는 고객 조회 성공")
        void getCustomer_success() {
            Customer customer = mock(Customer.class);
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

            Customer result = customerService.getCustomer(1L);

            assertThat(result).isEqualTo(customer);
        }

        @Test
        @DisplayName("존재하지 않는 고객 조회 시 예외 발생")
        void getCustomer_notFound_throwsException() {
            when(customerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomer(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 고객입니다");
        }
    }

    // ── 고객 생성 ──────────────────────────────────────

    @Nested
    @DisplayName("고객 생성 (createCustomer)")
    class CreateCustomerTests {

        @Test
        @DisplayName("신규 고객 등록 성공")
        void createCustomer_success() {
            User user = mock(User.class);
            Customer savedCustomer = mock(Customer.class);
            when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

            Customer result = customerService.createCustomer(
                    user, "테스트회사", "홍길동", "hong@test.com",
                    "010-1234-5678", "123-45-67890", "서울시 강남구", "메모"
            );

            assertThat(result).isEqualTo(savedCustomer);
            verify(customerRepository, times(1)).save(any(Customer.class));
        }
    }
}
