package com.novatech.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Couverture du panier : somme des lignes, frais de port, bornes de saisie. */
class CartTest {

    private static Cart cartFor(String... unitPrices) {
        Cart cart = new Cart(8842L, new Customer("C-1001", "STANDARD"));
        int index = 0;
        for (String price : unitPrices) {
            cart.addLine(new CartLine("NT-" + (4471 + index++), 1, new BigDecimal(price)));
        }
        return cart;
    }

    @Test
    void sums_lines_excluding_shipping() {
        Cart cart = new Cart(1L, new Customer("C-1001", "STANDARD"));
        cart.addLine(new CartLine("NT-4471", 30, new BigDecimal("12.40")));
        cart.addLine(new CartLine("NT-4472", 2, new BigDecimal("155.00")));

        assertThat(cart.totalExclVat()).isEqualByComparingTo(new BigDecimal("682.00"));
    }

    @Test
    void returns_zero_for_an_empty_cart() {
        Cart cart = new Cart(1L, new Customer("C-1001", "STANDARD"));

        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.totalExclVat()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void charges_full_shipping_below_five_hundred() {
        assertThat(cartFor("100.00").shippingCost(new BigDecimal("100.00")))
                .isEqualByComparingTo(new BigDecimal("24.90"));
    }

    @Test
    void charges_reduced_shipping_between_five_hundred_and_two_thousand() {
        assertThat(cartFor("600.00").shippingCost(new BigDecimal("600.00")))
                .isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void offers_shipping_from_two_thousand() {
        assertThat(cartFor("2500.00").shippingCost(new BigDecimal("2500.00")))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void adds_the_oversized_surcharge_whatever_the_amount() {
        Cart cart = new Cart(1L, new Customer("C-1001", "STANDARD"));
        cart.addLine(new CartLine("NT-9001", 1, new BigDecimal("3000.00"), true));

        assertThat(cart.shippingCost(new BigDecimal("3000.00")))
                .isEqualByComparingTo(new BigDecimal("89.00"));
    }

    @Test
    void rejects_a_line_with_a_non_positive_amount() {
        assertThatThrownBy(() -> new CartLine("NT-4471", 1, new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_quantity_above_the_maximum() {
        assertThatThrownBy(() -> new CartLine("NT-4471", 10_000, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_quantity_below_one() {
        assertThatThrownBy(() -> new CartLine("NT-4471", 0, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
