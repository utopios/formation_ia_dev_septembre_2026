package com.novatech.orders.web;

import java.math.BigDecimal;

/**
 * DTO de reponse du recalcul de panier.
 *
 * <p>La couche web n'expose jamais une entite de persistance : le tunnel
 * recoit ce DTO, pas l'agregat {@code Cart}. Le detail de la remise est
 * expose parce que les clients appellent le service client pour comprendre
 * leur remise.</p>
 *
 * @param amountExclVat       montant HT des articles avant remise
 * @param discountRate        taux de remise applique, en pourcentage
 * @param discountAmount      montant de la remise en euros
 * @param discountOrigin      origine retenue : palier de volume, contrat, geste
 * @param amountAfterDiscount montant HT des articles apres remise
 * @param shippingCost        frais de port HT
 * @param totalInclVat        total TTC
 */
public record CartResponse(BigDecimal amountExclVat,
                           BigDecimal discountRate,
                           BigDecimal discountAmount,
                           String discountOrigin,
                           BigDecimal amountAfterDiscount,
                           BigDecimal shippingCost,
                           BigDecimal totalInclVat) {
}
