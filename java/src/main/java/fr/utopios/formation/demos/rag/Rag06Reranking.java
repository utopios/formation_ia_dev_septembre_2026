package fr.utopios.formation.demos.rag;

import fr.utopios.formation.demos.rag.JeuNovatech.Question;

import com.fasterxml.jackson.databind.JsonNode;
import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.commun.Recherche.Resultat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RAG 06 (Module 5) : le reranking — elargir au vectoriel, resserrer par
 * une seconde passe.
 *
 * <p>Le vectoriel rend vite un top 8 « a peu pres bon ». Le reranking relit
 * ces 8 passages avec un juge plus couteux mais plus fin, et ne garde que
 * les meilleurs. Deux juges, mesures sur huit questions dont on connait la
 * section qui repond :</p>
 * <ol>
 *   <li><b>pondere</b> : 0,6 × cosinus + 0,4 × recouvrement lexical — le
 *       pattern des slides et du TP 4, deterministe, gratuit ;</li>
 *   <li><b>LLM listwise</b> : le modele de chat recoit la question et les
 *       8 passages numerotes, et rend un classement en JSON — le
 *       « cross-encoder » du pauvre, avec un vrai cout et une vraie
 *       fragilite.</li>
 * </ol>
 *
 * <p>Mesure : hit@1, hit@3 et MRR avant et apres chaque reranking, le temps
 * du juge LLM, et le nombre de classements JSON invalides — car un petit
 * modele rend parfois une liste incomplete, et le harnais doit s'en apercevoir.</p>
 *
 * <p>Lancement :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.rag.Rag06Reranking"}</p>
 */
public final class Rag06Reranking {

    private static final List<Question> QUESTIONS = JeuNovatech.QUESTIONS;

    private static final int SUR_ECHANTILLON = 8;

    private Rag06Reranking() {
    }

    public static void main(String[] args) {
        System.out.println("=== RAG 06 : reranking — elargir au vectoriel, resserrer par une seconde passe ===");
        System.out.println();

        List<Chunk> corpus = JeuNovatech.corpus();
        List<double[]> vecteurs = Llm.embedDocuments(corpus.stream().map(Chunk::texte).toList());
        System.out.printf("Corpus : %d sections. Sur-echantillon vectoriel : top %d. Juge LLM : %s%n%n",
                corpus.size(), SUR_ECHANTILLON, Llm.modeleChat());

        Map<String, List<Integer>> rangs = new LinkedHashMap<>();
        for (String m : List.of("vectoriel", "pondere", "LLM listwise")) {
            rangs.put(m, new ArrayList<>());
        }
        long dureeLlm = 0;
        int jsonInvalides = 0;

        System.out.printf("%-58s %-18s %10s %10s %13s%n", "Question", "attendu", "vectoriel", "pondere", "LLM listwise");
        for (Question q : QUESTIONS) {
            // 1. Elargir : le top 8 vectoriel.
            List<Resultat> vectoriel = Recherche.classerCosinus(corpus, vecteurs, Llm.embedQuery(q.texte()))
                    .subList(0, SUR_ECHANTILLON);

            // 2. Resserrer, juge 1 : score pondere.
            List<Resultat> pondere = vectoriel.stream()
                    .map(r -> new Resultat(r.chunk(), 0.6 * r.score() + 0.4 * Recherche.recouvrement(q.texte(), r.chunk())))
                    .sorted(Comparator.comparingDouble(Resultat::score).reversed())
                    .toList();

            // 3. Resserrer, juge 2 : le modele classe les 8 passages.
            long t = System.currentTimeMillis();
            List<Resultat> llm = rerankLlm(q.texte(), vectoriel);
            dureeLlm += System.currentTimeMillis() - t;
            if (llm == null) {
                jsonInvalides++;
                llm = vectoriel;
            }

            int rv = Recherche.rang(vectoriel, q.attendu(), SUR_ECHANTILLON);
            int rp = Recherche.rang(pondere, q.attendu(), SUR_ECHANTILLON);
            int rl = Recherche.rang(llm, q.attendu(), SUR_ECHANTILLON);
            rangs.get("vectoriel").add(rv);
            rangs.get("pondere").add(rp);
            rangs.get("LLM listwise").add(rl);
            System.out.printf("%-58s %-18s %10s %10s %13s%n", Recherche.tronquer(q.texte(), 58), q.libelleAttendu(),
                    rang(rv), rang(rp), rang(rl));
        }

        System.out.println();
        System.out.println("--- Synthese sur " + QUESTIONS.size() + " questions (rang dans le top 8) ---");
        System.out.printf("%-14s %6s %6s %7s%n", "", "hit@1", "hit@3", "MRR");
        rangs.forEach((m, rs) -> {
            long h1 = rs.stream().filter(x -> x == 1).count();
            long h3 = rs.stream().filter(x -> x >= 1 && x <= 3).count();
            double mrr = rs.stream().mapToDouble(x -> x == 0 ? 0 : 1.0 / x).average().orElse(0);
            System.out.printf(Locale.ROOT, "%-14s %3d/%-2d %3d/%-2d %7.3f%n", m, h1, rs.size(), h3, rs.size(), mrr);
        });
        System.out.printf("%nCout du juge LLM : %.1f s pour %d questions (%.1f s par question), %d classement(s) JSON invalide(s).%n",
                dureeLlm / 1000.0, QUESTIONS.size(), dureeLlm / 1000.0 / QUESTIONS.size(), jsonInvalides);

        // Le detail d'une question ou le vectoriel se trompe.
        Question q = QUESTIONS.get(0);
        System.out.println();
        System.out.println("--- Detail : « " + q.texte() + " » (attendu : " + q.libelleAttendu() + ") ---");
        List<Resultat> vectoriel = Recherche.classerCosinus(corpus, vecteurs, Llm.embedQuery(q.texte())).subList(0, SUR_ECHANTILLON);
        System.out.printf("%-4s %-40s %8s %8s %8s%n", "#", "section (ordre vectoriel)", "cosinus", "recouvr.", "pondere");
        for (int i = 0; i < vectoriel.size(); i++) {
            Resultat r = vectoriel.get(i);
            double rec = Recherche.recouvrement(q.texte(), r.chunk());
            System.out.printf(Locale.ROOT, "%-4d %-40s %8.3f %8.2f %8.3f%s%n", i + 1,
                    Recherche.tronquer(r.chunk().citation(), 40), r.score(), rec, 0.6 * r.score() + 0.4 * rec,
                    q.attendu().test(r.chunk()) ? "   <- attendu" : "");
        }

        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Le reranking ne cherche pas : il relit 8 passages deja trouves. S'il n'y est pas, rien ne le fera remonter.");
        System.out.println("  Le juge pondere est gratuit et deterministe ; le juge LLM coute des secondes et rend parfois un JSON");
        System.out.println("  inexploitable — un harnais compte ces cas et retombe sur l'ordre precedent, jamais sur une erreur.");
    }

    /**
     * Le modele recoit les passages numerotes et rend {@code {"classement":[3,1,...]}}.
     * On valide la reponse : que des numeros existants, sans doublon ; les
     * passages oublies sont ajoutes a la fin dans l'ordre vectoriel. Si le
     * JSON est inexploitable, on rend null et l'appelant garde l'ordre initial.
     */
    static List<Resultat> rerankLlm(String question, List<Resultat> passages) {
        StringBuilder sb = new StringBuilder("QUESTION : ").append(question).append("\n\nPASSAGES :\n");
        for (int i = 0; i < passages.size(); i++) {
            sb.append("\n[").append(i + 1).append("] ").append(Recherche.tronquer(passages.get(i).chunk().texte(), 500)).append("\n");
        }
        sb.append("\nClasse les passages du plus utile au moins utile pour repondre a la question. ")
                .append("Reponds en JSON : {\"classement\": [numeros, du plus utile au moins utile]}");
        try {
            JsonNode json = Llm.chatJson(sb.toString(),
                    "Tu es un juge de pertinence. Tu ne reponds pas a la question : tu classes les passages.");
            JsonNode liste = json.path("classement");
            if (!liste.isArray() || liste.isEmpty()) {
                return null;
            }
            List<Resultat> ordre = new ArrayList<>();
            for (JsonNode n : liste) {
                int i = n.asInt(-1) - 1;
                if (i >= 0 && i < passages.size() && ordre.stream().noneMatch(r -> r == passages.get(i))) {
                    ordre.add(passages.get(i));
                }
            }
            if (ordre.isEmpty()) {
                return null;
            }
            for (Resultat r : passages) {
                if (!ordre.contains(r)) {
                    ordre.add(r);
                }
            }
            return ordre;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String rang(int r) {
        return r == 0 ? "-" : String.valueOf(r);
    }
}
