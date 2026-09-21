package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Remise retenue pour un panier ou une commande : son taux, son montant et
 * son origine.
 *
 * <p>Une commande ne beneficie que d'une seule remise, la plus avantageuse
 * pour le client parmi celles auxquelles il a droit. Les remises ne se
 * cumulent jamais : cette classe porte donc un taux unique et non une liste.</p>
 *
 * @param rate   taux applique, en pourcentage
 * @param amount montant de la remise en euros, arrondi au centime
 * @param origin origine retenue, tracee pour l'audit
 */
public record Discount(BigDecimal rate, BigDecimal amount, DiscountOrigin origin) {

    public Discount {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(origin, "origin");
    }

    /** Remise nulle : panier vide, ou client sans droit a remise. */
    public static Discount none() {
        return new Discount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                DiscountOrigin.NONE);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
