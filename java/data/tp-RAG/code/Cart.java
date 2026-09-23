package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agregat panier : porte ses lignes, son proprietaire et le montant calcule.
 *
 * <p>Un panier appartient a un client et a un seul. Il expire apres 30 jours
 * d'inactivite et est purge a 90 jours par un traitement d'exploitation, hors
 * du domaine.</p>
 */
public final class Cart {

    /** Supplement de port forfaitaire pour un produit hors gabarit. */
    private static final BigDecimal OVERSIZED_SHIPPING_SURCHARGE = new BigDecimal("89.00");

    private final long cartId;
    private final Customer customer;
    private final List<CartLine> lines = new ArrayList<>();

    public Cart(long cartId, Customer customer) {
        this.cartId = cartId;
        this.customer = Objects.requireNonNull(customer, "customer");
    }

    public long getCartId() {
        return cartId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<CartLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Cart addLine(CartLine line) {
        lines.add(Objects.requireNonNull(line, "line"));
        return this;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Somme des lignes, frais de port exclus. C'est l'assiette de la remise :
     * la remise ne porte jamais sur le port ni sur la TVA.
     *
     * @return le montant HT des articles, arrondi au centime
     */
    public BigDecimal totalExclVat() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            total = total.add(line.totalExclVat());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Frais de port HT, calcules sur le montant HT apres remise : une remise
     * peut donc faire basculer le panier dans la tranche du port offert.
     *
     * @param amountAfterDiscount montant HT des articles apres remise
     * @return les frais de port HT, supplement hors gabarit inclus
     */
    public BigDecimal shippingCost(BigDecimal amountAfterDiscount) {
        BigDecimal shipping;
        if (amountAfterDiscount.compareTo(new BigDecimal("500.00")) < 0) {
            shipping = new BigDecimal("24.90");
        } else if (amountAfterDiscount.compareTo(new BigDecimal("2000.00")) < 0) {
            shipping = new BigDecimal("12.50");
        } else {
            shipping = BigDecimal.ZERO;
        }
        boolean hasOversizedLine = lines.stream().anyMatch(CartLine::isOversized);
        if (hasOversizedLine) {
            shipping = shipping.add(OVERSIZED_SHIPPING_SURCHARGE);
        }
        return shipping.setScale(2, RoundingMode.HALF_UP);
    }
}
