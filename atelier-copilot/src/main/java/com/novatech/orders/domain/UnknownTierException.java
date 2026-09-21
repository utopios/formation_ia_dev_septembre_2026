package com.novatech.orders.domain;

import java.math.BigDecimal;

/**
 * Levee lorsqu'aucun palier du bareme ne couvre le couple categorie / montant
 * presente. Signale une incoherence du bareme lui-meme, pas une erreur de
 * saisie de l'utilisateur.
 */
public class UnknownTierException extends BusinessException {

    public UnknownTierException(String category, BigDecimal amountExclVat) {
        super("DISCOUNT_TIER_NOT_FOUND",
                "Aucun palier de remise pour la categorie " + category
                        + " et le montant " + amountExclVat);
    }
}
