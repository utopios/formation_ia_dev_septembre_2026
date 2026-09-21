package com.novatech.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Couverture du bareme de remise, paliers et bonus de categorie.
 *
 * <p>Les montants attendus sont ceux des criteres d'acceptation Gherkin de la
 * story « Appliquer un bareme de remise par volume sur les commandes B2B ».</p>
 */
class DiscountPolicyTest {

    private DiscountPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DiscountPolicy();
    }

    private static Customer standard() {
        return new Customer("C-1001", "STANDARD");
    }

    private static Customer privilege() {
        return new Customer("C-2002", "PRIVILEGE");
    }

    private static Customer keyAccount() {
        return new Customer("C-3003", "KEY_ACCOUNT");
    }

    @Nested
    @DisplayName("Bareme par volume")
    class VolumeTiers {

        @Test
        void applies_no_discount_when_amount_below_first_tier() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("940.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(policy.computeVolumeDiscount(standard(), new BigDecimal("940.00")))
                    .isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        void applies_three_percent_when_amount_reaches_one_thousand() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("1000.00")))
                    .isEqualByComparingTo(new BigDecimal("3"));
        }

        @Test
        void applies_three_percent_just_below_the_five_thousand_threshold() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("4999.99")))
                    .isEqualByComparingTo(new BigDecimal("3"));
        }

        @Test
        void applies_six_percent_when_amount_reaches_five_thousand() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("5000.00")))
                    .isEqualByComparingTo(new BigDecimal("6"));
            assertThat(policy.computeVolumeDiscount(standard(), new BigDecimal("5000.00")))
                    .isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        void applies_nine_percent_when_amount_reaches_twenty_thousand() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("20000.00")))
                    .isEqualByComparingTo(new BigDecimal("9"));
            assertThat(policy.computeVolumeDiscount(standard(), new BigDecimal("20000.00")))
                    .isEqualByComparingTo(new BigDecimal("1800.00"));
        }
    }

    @Nested
    @DisplayName("Bonus de categorie")
    class CategoryBonus {

        @Test
        void adds_two_points_for_privilege_customer() {
            assertThat(policy.volumeDiscountRate(privilege(), new BigDecimal("5000.00")))
                    .isEqualByComparingTo(new BigDecimal("8"));
        }

        @Test
        void adds_four_points_for_key_account_customer() {
            assertThat(policy.volumeDiscountRate(keyAccount(), new BigDecimal("5000.00")))
                    .isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void adds_no_point_for_standard_customer() {
            assertThat(policy.volumeDiscountRate(standard(), new BigDecimal("5000.00")))
                    .isEqualByComparingTo(new BigDecimal("6"));
        }

        @Test
        void applies_bonus_even_when_no_volume_tier_is_reached() {
            assertThat(policy.volumeDiscountRate(keyAccount(), new BigDecimal("500.00")))
                    .isEqualByComparingTo(new BigDecimal("4"));
        }
    }

    @Nested
    @DisplayName("Non-cumul et choix de la remise la plus avantageuse")
    class BestDiscount {

        @Test
        void returns_zero_discount_for_an_empty_cart() {
            Discount discount = policy.computeBestDiscount(standard(), new BigDecimal("0.00"));
            assertThat(discount.amount()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(discount.origin()).isEqualTo(DiscountOrigin.NONE);
        }

        @Test
        void keeps_contract_rate_when_more_advantageous_than_volume_tier() {
            Customer customer = new Customer("C-4004", "STANDARD",
                    new BigDecimal("80000.00"), new BigDecimal("10"));
            Discount discount = policy.computeBestDiscount(customer, new BigDecimal("5000.00"));

            assertThat(discount.rate()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(discount.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(discount.origin()).isEqualTo(DiscountOrigin.CONTRACT);
        }

        @Test
        void keeps_volume_tier_when_more_advantageous_than_contract_rate() {
            Customer customer = new Customer("C-4005", "KEY_ACCOUNT",
                    new BigDecimal("80000.00"), new BigDecimal("5"));
            Discount discount = policy.computeBestDiscount(customer, new BigDecimal("25000.00"));

            assertThat(discount.rate()).isEqualByComparingTo(new BigDecimal("13"));
            assertThat(discount.origin()).isEqualTo(DiscountOrigin.VOLUME_TIER);
        }

        @Test
        void never_adds_contract_rate_to_volume_tier() {
            Customer customer = new Customer("C-4006", "PRIVILEGE",
                    new BigDecimal("80000.00"), new BigDecimal("7"));
            Discount discount = policy.computeBestDiscount(customer, new BigDecimal("5000.00"));

            assertThat(discount.rate()).isEqualByComparingTo(new BigDecimal("8"));
            assertThat(discount.rate()).isLessThan(new BigDecimal("15"));
        }
    }

    @Nested
    @DisplayName("Plafond de remise")
    class Cap {

        @Test
        void rejects_a_manual_rate_above_the_cap() {
            assertThatThrownBy(() -> policy.checkRateWithinCap(new BigDecimal("22")))
                    .isInstanceOf(DiscountCapExceededException.class)
                    .hasMessageContaining("15");
        }

        @Test
        void accepts_a_manual_rate_exactly_at_the_cap() {
            policy.checkRateWithinCap(new BigDecimal("15"));
        }

        @Test
        void accepts_a_manual_rate_below_the_cap() {
            policy.checkRateWithinCap(new BigDecimal("12.5"));
        }
    }

    @Nested
    @DisplayName("Arrondis")
    class Rounding {

        @Test
        void rounds_the_discount_amount_to_the_nearest_cent_half_up() {
            BigDecimal discount = policy.computeVolumeDiscount(standard(), new BigDecimal("1234.57"));
            assertThat(discount).isEqualByComparingTo(new BigDecimal("37.04"));
        }

        @Test
        void produces_a_two_decimal_scale() {
            BigDecimal discount = policy.computeVolumeDiscount(standard(), new BigDecimal("5000.00"));
            assertThat(discount.scale()).isEqualTo(2);
        }
    }
}
