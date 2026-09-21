package com.novatech.orders.domain;

/**
 * Origine de la remise retenue.
 *
 * <p>La table {@code order_discount} ne porte pas encore cette information :
 * son ajout est l'objet du ticket NOVA-4, qui conditionne l'export nocturne
 * vers le referentiel comptable.</p>
 */
public enum DiscountOrigin {

    /** Aucune remise applicable. */
    NONE,

    /** Bareme par volume, bonus de categorie inclus. */
    VOLUME_TIER,

    /** Taux negocie au contrat cadre. */
    CONTRACT,

    /** Geste commercial saisi manuellement, avec motif et validation. */
    COMMERCIAL_GESTURE
}
