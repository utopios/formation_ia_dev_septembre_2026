package fr.utopios.formation.demos;

import fr.utopios.formation.commun.Llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Demo 10d (Module 6) : consommation abusive (OWASP LLM10), en simulation, et
 * les deux bornes qui la contiennent.
 *
 * <p>Un LLM se facture au token et se partage entre utilisateurs. Un prompt
 * qui reclame une sortie enorme, ou un client qui enchaine les requetes,
 * epuise le service pour tout le monde — sans rien « pirater ». Deux
 * scenarios, mesures avec les compteurs de {@link Llm} :</p>
 * <ol>
 *   <li><b>la sortie sans fin</b> : « ecris les nombres de 1 a 800, un par
 *       ligne » — sans plafond, puis avec {@code num_predict} (le plafond de
 *       tokens en sortie, pose par le harness, que le prompt ne peut pas
 *       lever) ;</li>
 *   <li><b>la rafale</b> : douze requetes d'affilee du meme client, avec un
 *       limiteur de debit cote harness (5 par minute) : celles au-dela sont
 *       refusees AVANT d'atteindre le modele.</li>
 * </ol>
 *
 * <p>Lancement :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.Demo10dConsommationAbusive"}</p>
 */
public final class Demo10dConsommationAbusive {

    private static final String NORMALE = "Quel est le plafond de remise chez NovaTech ? Reponds en une phrase.";
    private static final String ABUSIVE = "Ecris les nombres de 1 a 800, un par ligne, sans rien d'autre.";
    private static final int PLAFOND_TOKENS = 150;

    /** Le limiteur de debit : N requetes par fenetre glissante, par client. */
    static final class LimiteurDebit {
        private final int max;
        private final long fenetreMs;
        private final Deque<Long> horodatages = new ArrayDeque<>();

        LimiteurDebit(int max, long fenetreMs) {
            this.max = max;
            this.fenetreMs = fenetreMs;
        }

        synchronized boolean autoriser(long maintenant) {
            while (!horodatages.isEmpty() && maintenant - horodatages.peekFirst() > fenetreMs) {
                horodatages.pollFirst();
            }
            if (horodatages.size() >= max) {
                return false;
            }
            horodatages.addLast(maintenant);
            return true;
        }
    }

    private Demo10dConsommationAbusive() {
    }

    public static void main(String[] args) {
        System.out.println("=== Demo 10d : consommation abusive, et les bornes du harness ===");
        System.out.println("Modele : " + Llm.modeleChat());
        System.out.println();

        // ---- Scenario 1 : la sortie sans fin ------------------------------------
        System.out.println("--- Scenario 1 : la sortie sans fin ---");
        mesurer("requete normale", NORMALE, -1);
        long tokensSansPlafond = mesurer("prompt abusif, SANS plafond", ABUSIVE, -1);
        long tokensAvecPlafond = mesurer("prompt abusif, plafond num_predict=" + PLAFOND_TOKENS, ABUSIVE, PLAFOND_TOKENS);
        System.out.printf(Locale.ROOT, "  -> le plafond divise le cout de la sortie par %.0f, quoi que demande le prompt.%n%n",
                tokensAvecPlafond == 0 ? 0 : (double) tokensSansPlafond / tokensAvecPlafond);

        // ---- Scenario 2 : la rafale --------------------------------------------
        System.out.println("--- Scenario 2 : la rafale, 12 requetes du meme client, limiteur 5 par minute ---");
        LimiteurDebit limiteur = new LimiteurDebit(5, 60_000);
        int servies = 0;
        int refusees = 0;
        long tokensRafale = 0;
        for (int i = 1; i <= 12; i++) {
            if (!limiteur.autoriser(System.currentTimeMillis())) {
                refusees++;
                System.out.printf("  requete %2d : REFUSEE par le limiteur (aucun appel au modele)%n", i);
                continue;
            }
            Llm.chat(List.of(Llm.Message.utilisateur(NORMALE)), 0.0, 60);
            servies++;
            tokensRafale += Llm.derniersTokensPrompt() + Llm.derniersTokensReponse();
            System.out.printf("  requete %2d : servie, %d ms%n", i, Llm.dernieresDureeMs());
        }
        System.out.printf("  -> %d servies (%d tokens), %d refusees sans toucher le modele.%n%n", servies, tokensRafale, refusees);

        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Deux bornes, toutes deux cote harness et invisibles du prompt : le plafond de tokens en sortie");
        System.out.println("  (num_predict) et le limiteur de debit par client. Sans elles, un seul utilisateur — ou un seul");
        System.out.println("  ticket piege qui demande « liste tout » — occupe le modele partage pour tous les autres.");
        System.out.println("  Un timeout par requete (Llm : 5 min) est la troisieme borne ; il arrive trop tard pour le cout.");
    }

    /** Un appel, et ses compteurs : tokens de sortie, duree. Rend les tokens de sortie. */
    private static long mesurer(String libelle, String prompt, int plafond) {
        String reponse = Llm.chat(List.of(Llm.Message.utilisateur(prompt)), 0.0, plafond);
        long tokens = Llm.derniersTokensReponse();
        System.out.printf(Locale.ROOT, "  %-42s %5d tokens de sortie, %6.1f s, %d lignes%n", libelle, tokens,
                Llm.dernieresDureeMs() / 1000.0, reponse.lines().count());
        return tokens;
    }
}
