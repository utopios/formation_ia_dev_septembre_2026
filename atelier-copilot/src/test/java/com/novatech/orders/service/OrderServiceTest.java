package com.novatech.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novatech.orders.domain.Cart;
import com.novatech.orders.domain.CartLine;
import com.novatech.orders.domain.Customer;
import com.novatech.orders.domain.DiscountOrigin;
import com.novatech.orders.domain.DiscountPolicy;
import com.novatech.orders.domain.Order;
import com.novatech.orders.domain.OrderStatus;
import com.novatech.orders.infrastructure.OrderDao;
import com.novatech.orders.infrastructure.SqlExecutor;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Couverture de l'orchestration : ordre de calcul, controles bloquants,
 * bascule en validation hierarchique.
 */
class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new DiscountPolicy(),
                new DiscountValidationService(),
                new OrderDao(new SqlExecutor()));
    }

    private static Cart cart(String category, String unitPrice, int quantity) {
        Cart cart = new Cart(8842L, new Customer("C-1001", category));
        cart.addLine(new CartLine("NT-4471", quantity, new BigDecimal(unitPrice)));
        return cart;
    }

    @Test
    void recalculates_an_empty_cart_without_raising_an_error() {
        Cart empty = new Cart(1L, new Customer("C-1001", "STANDARD"));

        OrderService.CartRecalculation result = service.recalculate(empty);

        assertThat(result.discount().amount()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.discount().origin()).isEqualTo(DiscountOrigin.NONE);
    }

    @Test
    void applies_the_discount_before_shipping_and_vat() {
        OrderService.CartRecalculation result = service.recalculate(cart("STANDARD", "500.00", 10));

        assertThat(result.amountExclVat()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.discount().rate()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(result.amountAfterDiscount()).isEqualByComparingTo(new BigDecimal("4700.00"));
        assertThat(result.shippingCost()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void never_discounts_the_shipping_cost() {
        OrderService.CartRecalculation result = service.recalculate(cart("STANDARD", "600.00", 1));

        assertThat(result.amountAfterDiscount()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(result.shippingCost()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void lets_a_discount_move_the_cart_into_the_free_shipping_bracket() {
        OrderService.CartRecalculation result = service.recalculate(cart("STANDARD", "2050.00", 1));

        assertThat(result.discount().rate()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(result.amountAfterDiscount()).isEqualByComparingTo(new BigDecimal("1988.50"));
        assertThat(result.shippingCost()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void rejects_an_order_below_the_minimum_amount() {
        Cart small = cart("STANDARD", "50.00", 2);

        assertThatThrownBy(() -> service.placeOrder(small, "CMD-1"))
                .isInstanceOf(OrderService.OrderValidationException.class)
                .hasMessageContaining("150 EUR HT");
    }

    @Test
    void rejects_an_empty_cart_at_validation() {
        Cart empty = new Cart(1L, new Customer("C-1001", "STANDARD"));

        assertThatThrownBy(() -> service.placeOrder(empty, "CMD-2"))
                .isInstanceOf(OrderService.OrderValidationException.class);
    }

    @Test
    void rejects_an_order_above_the_customer_credit_limit() {
        Cart cart = new Cart(1L, new Customer("C-1001", "STANDARD",
                new BigDecimal("1000.00"), null));
        cart.addLine(new CartLine("NT-4471", 10, new BigDecimal("500.00")));

        assertThatThrownBy(() -> service.placeOrder(cart, "CMD-3"))
                .isInstanceOf(OrderService.OrderValidationException.class)
                .hasMessageContaining("plafond de credit");
    }

    @Test
    void sends_the_order_to_invoicing_when_no_approval_is_needed() {
        Order order = service.placeOrder(cart("STANDARD", "500.00", 10), "CMD-4");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_INVOICING);
        assertThat(order.getDiscount().rate()).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void holds_the_order_for_approval_when_the_rate_exceeds_the_delegation_threshold() {
        Order order = service.placeOrder(cart("KEY_ACCOUNT", "1000.00", 25), "CMD-5");

        assertThat(order.getDiscount().rate()).isEqualByComparingTo(new BigDecimal("13"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_APPROVAL);
    }

    @Test
    void rejects_a_commercial_gesture_without_a_documented_reason() {
        Order order = service.placeOrder(cart("STANDARD", "500.00", 10), "CMD-6");

        assertThatThrownBy(() ->
                service.applyCommercialGesture(order, new BigDecimal("10"), "litige"))
                .isInstanceOf(OrderService.OrderValidationException.class);
    }

    @Test
    void accepts_a_commercial_gesture_with_a_documented_reason() {
        Order order = service.placeOrder(cart("STANDARD", "500.00", 10), "CMD-7");

        service.applyCommercialGesture(order, new BigDecimal("10"),
                "Geste de fidelisation apres litige de livraison du mois dernier");
    }
}
