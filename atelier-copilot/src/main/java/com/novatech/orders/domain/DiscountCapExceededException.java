package com.novatech.orders.domain;

import java.math.BigDecimal;

/**
 * Levee lorsqu'un taux de remise depasse le plafond autorise.
 *
 * <p>Le plafond est une regle applicative et non un simple avertissement :
 * une remise au-dela est rejetee par le calculateur. Seule une validation
 * nominative et horodatee du directeur commercial peut autoriser un
 * depassement.</p>
 */
public class DiscountCapExceededException extends BusinessException {

    public DiscountCapExceededException(BigDecimal requestedRate, BigDecimal cap) {
        super("DISCOUNT_CAP_EXCEEDED",
                "Taux de remise demande " + requestedRate
                        + " % superieur au plafond de " + cap + " %");
    }
}
