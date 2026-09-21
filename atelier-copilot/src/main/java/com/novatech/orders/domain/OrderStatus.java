package com.novatech.orders.domain;

/**
 * Cycle de vie d'une commande.
 *
 * <p>{@link #PENDING_INVOICING} est l'etat de repli lorsque l'API Sage est
 * indisponible a la validation : la commande est conservee et rejouee, elle
 * n'est jamais perdue ni rejetee a l'utilisateur.</p>
 */
public enum OrderStatus {

    /** En attente d'une validation hierarchique de la remise demandee. */
    PENDING_APPROVAL,

    /** Validee, en attente de transmission a la facturation. */
    PENDING_INVOICING,

    /** Transmise a Sage et facturee. */
    INVOICED,

    /** Expediee : la commande est immuable. */
    SHIPPED,

    /** Annulee : reste visible dans l'export des remises avec son indicateur. */
    CANCELLED
}
