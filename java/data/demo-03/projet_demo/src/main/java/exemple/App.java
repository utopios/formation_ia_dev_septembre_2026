package exemple;

/**
 * Petit projet jouet servant de bac a sable a l'agent (demos 3 et 4).
 *
 * <p>Un mini-service de calcul de remise commerciale. Volontairement simple,
 * il donne du grain a moudre a l'agent quand il analyse le projet.</p>
 *
 * <p>Contrairement au Remise.java de la demo 2, ce code-ci est CORRECT : le
 * plafond y est bien applique. Les deux fichiers se ressemblent a dessein, ce
 * qui permet de montrer en salle la difference entre le code fautif et le code
 * conforme a la spec.</p>
 */
public class App {

    /**
     * Calcule le montant apres remise fidelite.
     *
     * <p>Regle metier : 2 % de remise par annee de fidelite, plafonnee a 20 %.</p>
     */
    public static double calculRemise(double montant, int fideliteAnnees) {
        double taux = Math.min(0.02 * fideliteAnnees, 0.20);
        return montant * (1 - taux);
    }

    /** Applique la TVA a un montant hors taxes. */
    public static double factureTtc(double montantHt, double tva) {
        return montantHt * (1 + tva);
    }

    /** Surcharge avec le taux de TVA par defaut de 20 %. */
    public static double factureTtc(double montantHt) {
        return factureTtc(montantHt, 0.20);
    }

    public static void main(String[] args) {
        System.out.println(calculRemise(100.0, 3));  // 94.0
        System.out.println(factureTtc(100.0));       // 120.0
    }
}
