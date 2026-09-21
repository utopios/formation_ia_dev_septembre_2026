package com.novatech.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novatech.orders.service.DiscountValidationService.ApprovalLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Couverture des seuils de delegation, bornes incluses : ce sont les valeurs
 * qui declenchent ou non un circuit de validation humaine.
 */
class DiscountValidationServiceTest {

    private DiscountValidationService service;

    @BeforeEach
    void setUp() {
        service = new DiscountValidationService();
    }

    @Test
    void applies_automatically_up_to_eight_percent() {
        assertThat(service.requiredApproval(new BigDecimal("8")))
                .isEqualTo(ApprovalLevel.NONE);
        assertThat(service.requiresHumanApproval(new BigDecimal("8"))).isFalse();
    }

    @Test
    void requires_sales_manager_just_above_eight_percent() {
        assertThat(service.requiredApproval(new BigDecimal("8.01")))
                .isEqualTo(ApprovalLevel.SALES_MANAGER);
    }

    @Test
    void requires_sales_manager_up_to_twelve_percent() {
        assertThat(service.requiredApproval(new BigDecimal("12")))
                .isEqualTo(ApprovalLevel.SALES_MANAGER);
    }

    @Test
    void requires_sales_director_above_twelve_percent() {
        assertThat(service.requiredApproval(new BigDecimal("12.01")))
                .isEqualTo(ApprovalLevel.SALES_DIRECTOR);
    }

    @Test
    void requires_sales_director_up_to_fifteen_percent() {
        assertThat(service.requiredApproval(new BigDecimal("15")))
                .isEqualTo(ApprovalLevel.SALES_DIRECTOR);
    }

    @Test
    void rejects_a_rate_above_fifteen_percent() {
        assertThatThrownBy(() -> service.requiredApproval(new BigDecimal("15.01")))
                .isInstanceOf(DiscountValidationService.UnauthorizedDiscountException.class);
    }

    @Test
    void requires_no_approval_when_no_discount_applies() {
        assertThat(service.requiresHumanApproval(BigDecimal.ZERO)).isFalse();
    }
}
