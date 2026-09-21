package com.novatech.orders.domain;

import java.math.BigDecimal;

/**
 * Categorie commerciale d'un client professionnel.
 *
 * <p>Le code porte par chaque valeur est celui stocke dans la colonne
 * {@code customer.category} et celui echange avec Sage. Il est fige : le
 * renommer casserait la reprise de donnees et les exports comptables
 * conserves dix ans.</p>
 *
 * <p>Le bonus est exprime en points de pourcentage et s'ajoute au taux du
 * palier de volume, conformement a la page « Regles de remise » 2.4.</p>
 */
public enum CustomerCategory {

    /** Categorie par defaut a la creation du compte. */
    STANDARD("STANDARD", new BigDecimal("0")),

    /** Chiffre d'affaires HT des 12 derniers mois superieur ou egal a 60 000 EUR. */
    PRIVILEGE("PRIVILEGE", new BigDecimal("2")),

    /** Contrat cadre signe, attribution manuelle par la direction commerciale. */
    KEY_ACCOUNT("KEY_ACCOUNT", new BigDecimal("4"));

    private final String code;
    private final BigDecimal bonusPercentagePoints;

    CustomerCategory(String code, BigDecimal bonusPercentagePoints) {
        this.code = code;
        this.bonusPercentagePoints = bonusPercentagePoints;
    }

    public String code() {
        return code;
    }

    /** Bonus en points de pourcentage ajoute au taux du palier de volume. */
    public BigDecimal bonusPercentagePoints() {
        return bonusPercentagePoints;
    }

    /**
     * Resout un code de categorie tel qu'il est stocke en base.
     *
     * @param code code brut, deja normalise en majuscules par l'appelant
     * @return la categorie correspondante
     * @throws IllegalArgumentException si le code n'appartient pas au referentiel
     */
    public static CustomerCategory fromCode(String code) {
        for (CustomerCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Categorie client inconnue : " + code);
    }
}
