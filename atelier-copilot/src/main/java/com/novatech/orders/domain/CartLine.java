package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ligne de panier : une reference catalogue, une quantite, un prix unitaire HT.
 *
 * <p>Le prix unitaire est fige au moment de l'ajout au panier et non relu au
 * tarif courant : le client ne doit pas voir son total bouger entre deux
 * ecrans. La spec limite ce gel a 72 heures, au-dela le panier est recalcule
 * par un traitement separe.</p>
 */
public final class CartLine {

    private static final int MAX_QUANTITY = 9_999;

    private final String productReference;
    private final int quantity;
    private final BigDecimal unitPriceExclVat;
    private final boolean oversized;

    public CartLine(String productReference, int quantity, BigDecimal unitPriceExclVat) {
        this(productReference, quantity, unitPriceExclVat, false);
    }

    public CartLine(String productReference,
                    int quantity,
                    BigDecimal unitPriceExclVat,
                    boolean oversized) {
        this.productReference = Objects.requireNonNull(productReference, "productReference");
        this.unitPriceExclVat = Objects.requireNonNull(unitPriceExclVat, "unitPriceExclVat");
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "Quantite hors bornes pour " + productReference + " : " + quantity);
        }
        if (unitPriceExclVat.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Montant negatif ou nul sur la ligne " + productReference);
        }
        this.quantity = quantity;
        this.oversized = oversized;
    }

    public String getProductReference() {
        return productReference;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceExclVat() {
        return unitPriceExclVat;
    }

    /** Produit hors gabarit : declenche un supplement de port forfaitaire. */
    public boolean isOversized() {
        return oversized;
    }

    /** Montant HT de la ligne, sans arrondi intermediaire. */
    public BigDecimal totalExclVat() {
        return unitPriceExclVat.multiply(BigDecimal.valueOf(quantity));
    }
}
