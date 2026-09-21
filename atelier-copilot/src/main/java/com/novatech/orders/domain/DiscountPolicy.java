package com.novatech.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Applique le bareme de remise et le plafond global. C'est ici que vit la
 * regle des 15 % : aucune autre couche ne doit recalculer un taux. Le bareme
 * est celui de la page « Regles de remise » 2.4, applicable au 1er septembre
 * 2026. Principe de non-cumul : une commande ne beneficie que d'une seule
 * remise, la plus avantageuse ; le taux contractuel remplace le bareme.
 */
public class DiscountPolicy {

    /** Plafond global du taux de remise, en pourcentage. */
    public static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("15");

    /** Categorie de repli pour les fiches anterieures a la reprise de donnees. */
    private static final String DEFAULT_CATEGORY = "STANDARD";

    private static final int MONEY_SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Paliers du bareme par volume, ordonnes du plus eleve au plus bas afin
     * que le premier palier dont le seuil est atteint soit le bon.
     */
    private static final List<Tier> TIERS = List.of(
            new Tier(new BigDecimal("20000"), new BigDecimal("9")),
            new Tier(new BigDecimal("5000"), new BigDecimal("6")),
            new Tier(new BigDecimal("1000"), new BigDecimal("3")),
            new Tier(BigDecimal.ZERO, BigDecimal.ZERO));

    /**
     * Calcule la remise par volume, bonus de categorie inclus, sur le montant
     * HT des articles (frais de port exclus), arrondie au centime.
     */
    public BigDecimal computeVolumeDiscount(Customer customer, BigDecimal amountExclVat) {
        Tier tier = resolveTier(customer, amountExclVat);
        BigDecimal rate = tier.rate().add(categoryBonus(customer));
        return amountExclVat.multiply(rate)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Taux de remise par volume retenu, bonus de categorie inclus. Expose
     * separement du montant parce que la trace d'audit exige le taux autant
     * que la somme : la page « Regles de remise » impose de journaliser le
     * palier et le taux applique, pas seulement l'euro remise.
     *
     * @param customer      client de la commande
     * @param amountExclVat montant HT des articles, frais de port exclus
     * @return le taux de remise retenu, exprime en pourcentage et non en
     *         fraction : 6 signifie six pour cent
     */
    public BigDecimal volumeDiscountRate(Customer customer, BigDecimal amountExclVat) {
        Tier tier = resolveTier(customer, amountExclVat);
        return tier.rate().add(categoryBonus(customer));
    }

    private Tier resolveTier(Customer customer, BigDecimal amountExclVat) {
        String category = customer.getCategory().toUpperCase();
        return TIERS.stream()
                .filter(t -> t.matches(category, amountExclVat))
                .findFirst()
                .orElseThrow(() -> new UnknownTierException(category, amountExclVat));
    }

    private BigDecimal categoryBonus(Customer customer) {
        return CustomerCategory.fromCode(customer.getCategory().toUpperCase())
                .bonusPercentagePoints();
    }

    /**
     * Retient la remise la plus avantageuse pour le client parmi celles
     * auxquelles il a droit, sans jamais les cumuler.
     *
     * <p>Le taux contractuel est la reference quand il existe, mais si le
     * bareme par volume est plus avantageux sur la commande consideree, c'est
     * le bareme qui s'applique.</p>
     *
     * @param customer      client de la commande
     * @param amountExclVat montant HT des articles, frais de port exclus
     * @return la remise retenue, avec son taux, son montant et son origine
     */
    public Discount computeBestDiscount(Customer customer, BigDecimal amountExclVat) {
        if (amountExclVat.signum() == 0) {
            return Discount.none();
        }

        BigDecimal volumeRate = volumeDiscountRate(customer, amountExclVat);
        BigDecimal bestRate = volumeRate;
        DiscountOrigin origin = volumeRate.signum() == 0
                ? DiscountOrigin.NONE
                : DiscountOrigin.VOLUME_TIER;

        BigDecimal contractRate = customer.getContractDiscountRate().orElse(BigDecimal.ZERO);
        if (contractRate.compareTo(bestRate) > 0) {
            bestRate = contractRate;
            origin = DiscountOrigin.CONTRACT;
        }

        BigDecimal amount = amountExclVat.multiply(bestRate)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        return new Discount(bestRate, amount, origin);
    }

    /**
     * Verifie qu'un taux saisi manuellement reste dans les limites
     * autorisees. Utilise pour les gestes commerciaux, qui sortent du bareme.
     *
     * @param requestedRate taux demande, en pourcentage
     * @throws DiscountCapExceededException si le taux depasse le plafond
     */
    public void checkRateWithinCap(BigDecimal requestedRate) {
        if (requestedRate.compareTo(MAX_DISCOUNT_RATE) > 0) {
            throw new DiscountCapExceededException(requestedRate, MAX_DISCOUNT_RATE);
        }
    }

    /**
     * Palier du bareme par volume : un seuil de declenchement et le taux
     * associe. Le seuil est inclusif, conformement au bareme.
     */
    private record Tier(BigDecimal threshold, BigDecimal rate) {

        boolean matches(String category, BigDecimal amountExclVat) {
            return amountExclVat.compareTo(threshold) >= 0;
        }
    }
}
