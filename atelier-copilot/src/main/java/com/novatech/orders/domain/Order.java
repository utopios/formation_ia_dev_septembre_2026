package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Commande validee.
 *
 * <p>Une commande transmise a la facturation est immuable : une modification
 * posterieure produit une ligne corrective dans l'export des remises,
 * l'historique n'est jamais reecrit. C'est une exigence d'audit, la trace
 * etant conservee dix ans.</p>
 */
public final class Order {

    private final String orderNumber;
    private final Customer customer;
    private final BigDecimal amountExclVatBeforeDiscount;
    private final Discount discount;
    private final Instant createdAt;
    private OrderStatus status;

    public Order(String orderNumber,
                 Customer customer,
                 BigDecimal amountExclVatBeforeDiscount,
                 Discount discount,
                 OrderStatus status,
                 Instant createdAt) {
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        this.customer = Objects.requireNonNull(customer, "customer");
        this.amountExclVatBeforeDiscount =
                Objects.requireNonNull(amountExclVatBeforeDiscount, "amountExclVatBeforeDiscount");
        this.discount = Objects.requireNonNull(discount, "discount");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getAmountExclVatBeforeDiscount() {
        return amountExclVatBeforeDiscount;
    }

    public Discount getDiscount() {
        return discount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Montant HT des articles apres remise. */
    public BigDecimal amountExclVatAfterDiscount() {
        return amountExclVatBeforeDiscount.subtract(discount.amount());
    }

    /**
     * Fait avancer la commande dans son cycle de vie.
     *
     * @param newStatus nouvel etat
     */
    public void changeStatus(OrderStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
    }
}
