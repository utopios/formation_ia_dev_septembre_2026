package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Client professionnel : identite, categorie, plafond de credit et remise
 * contractuelle eventuelle.
 *
 * <p>La categorie est portee par une chaine et non par l'enumeration
 * {@link CustomerCategory} parce que la colonne {@code customer.category} est
 * nullable en base : 412 fiches creees avant 2021 n'ont jamais ete migrees.
 * Le mapping vers l'enumeration est donc fait au moment du calcul, la ou la
 * regle de repli est connue.</p>
 */
public final class Customer {

    private final String customerCode;
    private final String category;
    private final BigDecimal creditLimit;
    private final BigDecimal contractDiscountRate;

    /** Constructeur de lecture courante : un client sans contrat cadre. */
    public Customer(String customerCode, String category) {
        this(customerCode, category, new BigDecimal("50000.00"), null);
    }

    public Customer(String customerCode,
                    String category,
                    BigDecimal creditLimit,
                    BigDecimal contractDiscountRate) {
        this.customerCode = customerCode;
        this.category = category;
        this.creditLimit = creditLimit;
        this.contractDiscountRate = contractDiscountRate;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    /**
     * Code de categorie tel qu'il figure en base.
     *
     * @return le code, ou {@code null} pour une fiche anterieure a la reprise
     *         de donnees
     */
    public String getCategory() {
        return category;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    /**
     * Taux negocie annuellement pour les clients sous contrat cadre, exprime
     * en pourcentage. L'application est la source de verite depuis le
     * 1er septembre 2026 : le fichier Excel du service commercial ne l'est
     * plus.
     */
    public Optional<BigDecimal> getContractDiscountRate() {
        return Optional.ofNullable(contractDiscountRate);
    }
}
