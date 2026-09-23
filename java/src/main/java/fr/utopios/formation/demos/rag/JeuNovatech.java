package fr.utopios.formation.demos.rag;

import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.demos.Donnees;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Le jeu d'evaluation commun aux demos RAG 05, 06 et 07 : les quatre pages
 * Confluence de NovaTech, et huit questions dont on connait la section qui
 * repond.
 *
 * <p>Un jeu d'evaluation est ce qui transforme « ca marche » en un nombre. Il
 * est petit (huit questions), donc chaque question compte pour 12,5 points de
 * hit@1 : on lit les rangs question par question, pas seulement la moyenne.</p>
 */
public final class JeuNovatech {

    private JeuNovatech() {
    }

    /** Les pages, nommees court pour les citations. */
    public static final Map<String, String> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("spec", "rag/novatech/01-specification-fonctionnelle-tunnel-commande.md");
        PAGES.put("remise", "rag/novatech/02-regles-de-remise.md");
        PAGES.put("normes", "rag/novatech/03-normes-et-conventions-de-developpement.md");
        PAGES.put("archi", "rag/novatech/04-architecture-application-commandes.md");
    }

    /** Une question, le critere « ce chunk est la bonne section », et son libelle. */
    public record Question(String texte, Predicate<Chunk> attendu, String libelleAttendu) {
    }

    /** Le critere « ce chunk est la section n de la page » — vrai aussi pour un enfant de cette section. */
    public static Predicate<Chunk> section(String page, int numero) {
        return c -> c.document().equals(page) && c.section().startsWith(numero + ".");
    }

    public static final List<Question> QUESTIONS = List.of(
            new Question("Qui doit valider une remise de 13 % ?", section("remise", 4), "remise §4"),
            new Question("Quel taux de remise pour une commande de 7 000 EUR HT ?", section("remise", 2), "remise §2"),
            new Question("Quelle remise s'applique aux commandes recurrentes ?", section("remise", 9), "remise §9"),
            new Question("Comment nommer un fichier de migration Flyway ?", section("normes", 2), "normes §2"),
            new Question("Que devient une commande si l'API Sage ne repond pas ?", section("archi", 5), "archi §5"),
            new Question("Quel mode d'arrondi est impose pour les montants ?",
                    section("normes", 4).or(section("spec", 5)), "normes §4 / spec §5"),
            new Question("Quels frais de port pour une commande de 800 EUR HT apres remise ?", section("spec", 6), "spec §6"),
            new Question("Combien de temps le journal des remises est-il conserve ?",
                    section("spec", 9).or(section("archi", 6)), "spec §9 / archi §6"));

    /** Les pages decoupees par section : 35 chunks. */
    public static List<Chunk> corpus() {
        List<Chunk> corpus = new ArrayList<>();
        PAGES.forEach((nom, fichier) -> corpus.addAll(Recherche.sectionsMarkdown(nom, Donnees.lire(fichier))));
        return corpus;
    }

    /** Affiche le tableau des rangs et la synthese hit@1 / hit@3 / MRR d'un ensemble de techniques. */
    public static void synthese(Map<String, List<Integer>> rangs, Map<String, String> couts, Map<String, Long> dureesMs) {
        System.out.println();
        System.out.println("--- Synthese (rang de la section attendue, top 10) ---");
        System.out.printf("%-26s %6s %6s %7s %8s   %s%n", "technique", "hit@1", "hit@3", "MRR", "duree", "cout");
        rangs.forEach((nom, rs) -> {
            long h1 = rs.stream().filter(x -> x == 1).count();
            long h3 = rs.stream().filter(x -> x >= 1 && x <= 3).count();
            double mrr = rs.stream().mapToDouble(x -> x == 0 ? 0 : 1.0 / x).average().orElse(0);
            System.out.printf(java.util.Locale.ROOT, "%-26s %3d/%-2d %3d/%-2d %7.3f %6.1f s   %s%n", nom, h1, rs.size(),
                    h3, rs.size(), mrr, dureesMs.getOrDefault(nom, 0L) / 1000.0, couts.getOrDefault(nom, ""));
        });
        System.out.println();
        System.out.println("Questions :");
        for (int i = 0; i < QUESTIONS.size(); i++) {
            System.out.printf("  Q%d  %-62s -> %s%n", i + 1, QUESTIONS.get(i).texte(), QUESTIONS.get(i).libelleAttendu());
        }
    }
}
