package fr.utopios.formation.demos.rag;

import fr.utopios.formation.demos.Donnees;

import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.commun.Recherche.Resultat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RAG 01 (Module 5) : un RAG simple, de bout en bout, sur les documents de
 * NovaTech.
 *
 * <p>La chaine complete, sans base de donnees et sans framework, pour que
 * chaque etape soit visible :</p>
 * <ol>
 *   <li><b>Ingestion</b> : quatre pages Confluence, une section = un chunk,
 *       avec sa citation (page › section) ;</li>
 *   <li><b>Indexation</b> : un vecteur par chunk ({@code nomic-embed-text},
 *       prefixe document) ;</li>
 *   <li><b>Recherche</b> : le vecteur de la question (prefixe requete), les
 *       trois chunks les plus proches par cosinus ;</li>
 *   <li><b>Generation</b> : le modele repond a partir de ces trois passages
 *       seulement, cite [Sn] apres chaque fait, et refuse par une phrase
 *       exacte si les passages ne suffisent pas.</li>
 * </ol>
 *
 * <p>Chaque question est posee deux fois : SANS les documents (le modele seul)
 * et AVEC. La difference est la raison d'etre du RAG — et la troisieme
 * question, sans reponse dans le corpus, est la raison d'etre du refus.</p>
 *
 * <p>Lancement (modele 3b conseille pour la generation) :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.rag.Rag01Simple"}</p>
 */
public final class Rag01Simple {

    private static final Map<String, String> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("Specification", "rag/novatech/01-specification-fonctionnelle-tunnel-commande.md");
        PAGES.put("Regles de remise", "rag/novatech/02-regles-de-remise.md");
        PAGES.put("Normes", "rag/novatech/03-normes-et-conventions-de-developpement.md");
        PAGES.put("Architecture", "rag/novatech/04-architecture-application-commandes.md");
    }

    private static final int K = 3;

    /** La phrase de refus, EXACTE : c'est elle qu'un test automatique cherche. */
    private static final String REFUS = "Je ne trouve pas cette information dans les documents fournis.";

    private static final String SYSTEME_RAG = """
            Tu reponds UNIQUEMENT a partir des passages fournis, numerotes [S1], [S2], [S3].
            Apres chaque fait que tu avances, cite le passage entre crochets, par exemple [S2].
            Si les passages ne contiennent pas l'information demandee, reponds exactement :
            « %s » et rien d'autre.
            Reponds en francais, en trois phrases au plus.""".formatted(REFUS);

    private static final String SYSTEME_SEUL = """
            Tu es l'assistant de l'equipe Commandes de NovaTech Industries.
            Reponds en francais, en trois phrases au plus.""";

    private static final List<String> QUESTIONS = List.of(
            "Qui doit valider une remise de 13 % chez NovaTech ?",
            "Quel est le montant minimum d'une commande, et que se passe-t-il en dessous ?",
            "Quel est le delai de livraison standard d'une commande ?");

    private static final Pattern CITATION = Pattern.compile("\\[S[1-3]\\]");

    private Rag01Simple() {
    }

    public static void main(String[] args) {
        System.out.println("=== RAG 01 : un RAG simple de bout en bout, sur les documents NovaTech ===");
        System.out.println();

        // ---- 1. Ingestion --------------------------------------------------
        List<Chunk> corpus = new ArrayList<>();
        PAGES.forEach((nom, fichier) -> corpus.addAll(Recherche.sectionsMarkdown(nom, Donnees.lire(fichier))));
        int caracteres = corpus.stream().mapToInt(c -> c.texte().length()).sum();
        System.out.printf("[1] Ingestion   : %d pages, %d sections, %d caracteres (%d car. par section en moyenne)%n",
                PAGES.size(), corpus.size(), caracteres, caracteres / corpus.size());

        // ---- 2. Indexation -------------------------------------------------
        long t0 = System.currentTimeMillis();
        List<double[]> vecteurs = Llm.embedDocuments(corpus.stream().map(Chunk::texte).toList());
        System.out.printf("[2] Indexation  : %d vecteurs de dimension %d, en %.1f s (%s)%n",
                vecteurs.size(), vecteurs.get(0).length, (System.currentTimeMillis() - t0) / 1000.0,
                Llm.MODELE_EMBEDDING);
        System.out.printf("    Modele de generation : %s%n", Llm.modeleChat());
        System.out.println();

        // ---- 3 et 4. Pour chaque question : sans, puis avec ------------------
        int numero = 0;
        for (String question : QUESTIONS) {
            numero++;
            System.out.println("==================================================================");
            System.out.println("QUESTION " + numero + " : " + question);
            System.out.println("==================================================================");

            // Sans RAG : le modele seul, avec ce qu'il croit savoir.
            long t1 = System.currentTimeMillis();
            String seul = Llm.chat(question, SYSTEME_SEUL, 0.0);
            System.out.printf("%n  SANS documents (%.1f s) :%n    %s%n",
                    (System.currentTimeMillis() - t1) / 1000.0, Recherche.tronquer(seul, 400));

            // Avec RAG : recherche, puis generation contrainte.
            List<Resultat> classement = Recherche.classerCosinus(corpus, vecteurs, Llm.embedQuery(question));
            List<Resultat> passages = classement.subList(0, K);
            System.out.printf("%n  [3] Recherche : %d passages les plus proches (marge 1er/2e : %.3f)%n",
                    K, Recherche.marge(classement));
            StringBuilder contexte = new StringBuilder();
            for (int i = 0; i < K; i++) {
                Resultat r = passages.get(i);
                System.out.printf(Locale.ROOT, "      [S%d] %.3f  %-45s %s%n", i + 1, r.score(),
                        r.chunk().citation(), Recherche.tronquer(r.chunk().texte(), 60));
                contexte.append("[S").append(i + 1).append("] (").append(r.chunk().citation()).append(")\n")
                        .append(r.chunk().texte()).append("\n\n");
            }

            long t2 = System.currentTimeMillis();
            String avec = Llm.chat("PASSAGES :\n\n" + contexte + "QUESTION : " + question, SYSTEME_RAG, 0.0).strip();
            long duree = System.currentTimeMillis() - t2;
            System.out.printf("%n  [4] AVEC documents (%.1f s, %d tokens de prompt) :%n    %s%n",
                    duree / 1000.0, Llm.derniersTokensPrompt(), avec.replace("\n", "\n    "));

            // Ce que le harnais verifie, sans lire la reponse : citations et refus.
            long citations = CITATION.matcher(avec).results().count();
            boolean refus = avec.contains(REFUS);
            System.out.printf("%n  Verification : %d citation(s) [Sn], refus exact : %s%n", citations, refus ? "OUI" : "non");
            System.out.println();
        }

        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Le modele n'a pas change entre SANS et AVEC : seuls les passages ont change.");
        System.out.println("  La citation [Sn] et la phrase de refus exacte sont ce qu'un test peut verifier ;");
        System.out.println("  le reste de la reponse, un humain doit le lire en face des passages.");
        System.out.println("  Tout est en memoire : la persistance (pgvector) est RAG 02, le decoupage RAG 04.");
    }
}
