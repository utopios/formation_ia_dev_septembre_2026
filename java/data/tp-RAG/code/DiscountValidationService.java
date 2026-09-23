package com.novatech.orders.service;

import com.novatech.orders.domain.BusinessException;
import java.math.BigDecimal;

/**
 * Determine qui doit valider une remise avant que la commande parte en
 * facturation.
 *
 * <p>Les seuils viennent de la section « Seuils de delegation » de la page
 * « Regles de remise » : jusqu'a 8 % l'application decide seule, jusqu'a 12 %
 * le responsable commercial, jusqu'a 15 % le directeur commercial. Le
 * validateur dispose de 48 heures ouvrees ; sans reponse la demande est
 * refusee, il n'existe pas de validation tacite.</p>
 */
public class DiscountValidationService {

    /** Au-dela de ce taux, la remise n'est plus appliquee automatiquement. */
    public static final BigDecimal AUTOMATIC_THRESHOLD = new BigDecimal("8");

    /** Au-dela de ce taux, le responsable commercial ne suffit plus. */
    public static final BigDecimal SALES_MANAGER_THRESHOLD = new BigDecimal("12");

    /** Plafond absolu : au-dela, la remise est interdite. */
    public static final BigDecimal SALES_DIRECTOR_THRESHOLD = new BigDecimal("15");

    /**
     * Niveau de validation requis pour un taux donne.
     */
    public enum ApprovalLevel {
        /** Aucune validation : application automatique. */
        NONE,
        /** Validation du responsable commercial. */
        SALES_MANAGER,
        /** Validation nominative et horodatee du directeur commercial. */
        SALES_DIRECTOR
    }

    /**
     * Levee quand un taux depasse le plafond de delegation le plus eleve.
     * Aucun role ne peut valider un tel taux : la demande est rejetee, pas
     * mise en attente.
     */
    public static class UnauthorizedDiscountException extends BusinessException {
        public UnauthorizedDiscountException(BigDecimal rate) {
            super("DISCOUNT_NOT_DELEGABLE",
                    "Taux de remise " + rate + " % hors de tout seuil de delegation");
        }
    }

    /**
     * Retourne le niveau de validation exige pour appliquer ce taux.
     *
     * @param rate taux de remise demande, en pourcentage
     * @return le niveau de validation requis
     * @throws UnauthorizedDiscountException si le taux depasse 15 %
     */
    public ApprovalLevel requiredApproval(BigDecimal rate) {
        if (rate.compareTo(AUTOMATIC_THRESHOLD) <= 0) {
            return ApprovalLevel.NONE;
        }
        if (rate.compareTo(SALES_MANAGER_THRESHOLD) <= 0) {
            return ApprovalLevel.SALES_MANAGER;
        }
        if (rate.compareTo(SALES_DIRECTOR_THRESHOLD) <= 0) {
            return ApprovalLevel.SALES_DIRECTOR;
        }
        throw new UnauthorizedDiscountException(rate);
    }

    /**
     * Indique si la commande peut partir directement en facturation ou si
     * elle doit d'abord passer en validation hierarchique.
     *
     * @param rate taux de remise applique
     * @return vrai si une validation humaine est necessaire
     */
    public boolean requiresHumanApproval(BigDecimal rate) {
        return requiredApproval(rate) != ApprovalLevel.NONE;
    }
}
