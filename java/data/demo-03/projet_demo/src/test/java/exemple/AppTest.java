package exemple;

/**
 * Tests du mini-projet de demonstration.
 *
 * <p>Ecrits sans framework pour rester lisibles par l'agent et executables
 * sans dependance : le projet jouet est une DONNEE de la demo, il n'entre pas
 * dans le cycle de build Maven de la formation.</p>
 */
public class AppTest {

    static void testRemiseNormale() {
        assertEgal(94.0, App.calculRemise(100.0, 3), "remise normale");
    }

    static void testRemisePlafonnee() {
        // 15 ans donnent 30 % theoriques, mais le plafond ramene a 20 %.
        assertEgal(80.0, App.calculRemise(100.0, 15), "remise plafonnee");
    }

    static void testFactureTtc() {
        assertEgal(120.0, App.factureTtc(100.0), "facture TTC");
    }

    private static void assertEgal(double attendu, double obtenu, String libelle) {
        if (Math.abs(attendu - obtenu) > 1e-9) {
            throw new AssertionError(libelle + " : attendu " + attendu + ", obtenu " + obtenu);
        }
        System.out.println("OK - " + libelle);
    }

    public static void main(String[] args) {
        testRemiseNormale();
        testRemisePlafonnee();
        testFactureTtc();
    }
}
