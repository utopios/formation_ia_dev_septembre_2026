package fr.utopios.formation.tp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Bm25;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.commun.Recherche.Resultat;
import fr.utopios.formation.demos.Donnees;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TP RAG — solution de reference : l'assistant documentaire de l'equipe
 * Commandes, construit palier par palier sur le corpus reel de NovaTech
 * ({@code data/tp-RAG/}), et MESURE a chaque palier sur le meme jeu de
 * 22 questions.
 *
 * <ol>
 *   <li><b>Palier 1</b> — le socle naif : toutes les sources coupees a 500
 *       caracteres, cosinus, top 3, generation sourcee avec refus exact.</li>
 *   <li><b>Palier 2</b> — le chunking par source : sections Confluence, un
 *       ticket Jira par chunk, une methode Java par chunk, tableaux entiers.</li>
 *   <li><b>Palier 3</b> — le contexte et l'hybride : « Source / Section » dans
 *       le texte vectorise, BM25, fusion RRF.</li>
 *   <li><b>Palier 4</b> — le reranking : top 8 sur-echantillonne, juge pondere,
 *       top 3 a la generation.</li>
 *   <li><b>Palier 5</b> — le cloisonnement : un role par requete, le filtre
 *       AVANT la recherche, et le test de fuite sur les questions de la
 *       direction commerciale.</li>
 * </ol>
 *
 * <p>Variables : {@code PALIER_MAX} (defaut 5), {@code GENERATION=non} pour ne
 * mesurer que la recherche (rapide).</p>
 *
 * <p>Lancement :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.tp.TpRagSolution"}</p>
 */
public final class TpRagSolution {

    enum Role { DEVELOPPEUR, COMMERCIAL, DIRECTION }

    /** Une source : son dossier, et qui a le droit de la lire. La regle vit ICI, pas dans le prompt. */
    record Source(String nom, Set<Role> acces) {
    }

    static final List<Source> SOURCES = List.of(
            new Source("confluence", EnumSet.allOf(Role.class)),
            new Source("jira", EnumSet.allOf(Role.class)),
            new Source("code", EnumSet.of(Role.DEVELOPPEUR)),
            new Source("direction-commerciale", EnumSet.of(Role.DIRECTION)));

    record Question(int id, String texte, List<String> sources, List<String> sections, List<String> reponses, String acces) {
        boolean sansReponse() {
            return sources.isEmpty();
        }

        boolean confidentielle() {
            return "direction".equals(acces);
        }

        /** Le chunk attendu : la bonne source, et la bonne section — ou, a defaut, le texte de la reponse. */
        boolean attendu(Chunk c) {
            String source = c.document().substring(0, c.document().indexOf('/'));
            if (!sources.contains(source)) {
                return false;
            }
            return sections.stream().anyMatch(s -> normaliser(c.section()).contains(normaliser(s)))
                    || reponses.stream().anyMatch(r -> normaliser(c.texte()).contains(normaliser(r)));
        }
    }

    /** Le resultat d'une generation, verifie mecaniquement. */
    record Verdict(boolean fondee, boolean refus, boolean fuite, String reponse) {
    }

    static final String REFUS = "Je ne trouve pas cette information dans les documents fournis.";
    static final String SYSTEME = """
            Tu reponds UNIQUEMENT a partir des passages fournis, numerotes [S1], [S2], [S3].
            Apres chaque fait que tu avances, cite le passage entre crochets, par exemple [S2].
            Si les passages ne contiennent pas l'information demandee, reponds exactement :
            « %s » et rien d'autre.
            Reponds en francais, en deux phrases au plus.""".formatted(REFUS);

    private static final int PALIER_MAX = Integer.parseInt(env("PALIER_MAX", "5"));
    private static final boolean GENERATION = !"non".equalsIgnoreCase(env("GENERATION", "oui"));
    private static final int K = 3;
    private static final int SUR_ECHANTILLON = 8;

    private TpRagSolution() {
    }

    // -----------------------------------------------------------------
    // Un palier = une facon d'indexer et de chercher
    // -----------------------------------------------------------------

    /** Un index : les chunks, leurs vecteurs, et (a partir du palier 3) le BM25. */
    static final class Index {
        final List<Chunk> chunks;
        final List<double[]> vecteurs;
        final Bm25 bm25;

        Index(List<Chunk> chunks, boolean contexte, boolean lexical) {
            this.chunks = chunks;
            this.vecteurs = Llm.embedDocuments(chunks.stream()
                    .map(c -> contexte ? "Source : " + c.document() + "\nSection : " + c.section() + "\n\n" + c.texte() : c.texte())
                    .toList());
            this.bm25 = lexical ? new Bm25(chunks) : null;
        }

        /** Restreint l'index a ce qu'un role a le droit de lire — AVANT toute similarite. */
        Index pour(Role role) {
            List<Chunk> retenus = new ArrayList<>();
            List<double[]> v = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String source = chunks.get(i).document().substring(0, chunks.get(i).document().indexOf('/'));
                if (SOURCES.stream().anyMatch(s -> s.nom().equals(source) && s.acces().contains(role))) {
                    retenus.add(chunks.get(i));
                    v.add(vecteurs.get(i));
                }
            }
            return new Index(retenus, v, bm25 == null ? null : new Bm25(retenus));
        }

        private Index(List<Chunk> chunks, List<double[]> vecteurs, Bm25 bm25) {
            this.chunks = chunks;
            this.vecteurs = vecteurs;
            this.bm25 = bm25;
        }

        List<Resultat> chercher(String question, boolean rerank) {
            double[] q = Llm.embedQuery(question);
            List<Resultat> cosinus = Recherche.classerCosinus(chunks, vecteurs, q);
            List<Resultat> classement = bm25 == null ? cosinus : Recherche.rrf(60, cosinus, bm25.classer(question));
            if (!rerank) {
                return classement;
            }
            // Palier 4 : on elargit a 8, on relit avec un juge pondere, on rend l'ordre relu.
            Map<Chunk, Double> cos = new HashMap<>();
            cosinus.forEach(r -> cos.put(r.chunk(), r.score()));
            return classement.subList(0, Math.min(SUR_ECHANTILLON, classement.size())).stream()
                    .map(r -> new Resultat(r.chunk(), 0.6 * cos.get(r.chunk()) + 0.4 * Recherche.recouvrement(question, r.chunk())))
                    .sorted(Comparator.comparingDouble(Resultat::score).reversed())
                    .toList();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== TP RAG : l'assistant documentaire de l'equipe Commandes, palier par palier ===");
        System.out.println("    Corpus : data/tp-RAG — LLM : " + Llm.modeleChat() + " — generation : " + (GENERATION ? "oui" : "non"));
        List<Question> questions = questions();
        List<Question> avecReponse = questions.stream().filter(q -> !q.sansReponse()).toList();
        System.out.printf("    %d questions : %d avec reponse (dont %d confidentielles), %d sans reponse%n%n",
                questions.size(), avecReponse.size(), avecReponse.stream().filter(Question::confidentielle).count(),
                questions.size() - avecReponse.size());

        Map<String, String> bilan = new LinkedHashMap<>();
        Index index = null;
        for (int palier = 1; palier <= PALIER_MAX; palier++) {
            System.out.println("##################################################################");
            System.out.println("PALIER " + palier + " — " + titre(palier));
            System.out.println("##################################################################");
            long t0 = System.currentTimeMillis();
            switch (palier) {
                case 1 -> index = new Index(chunksNaifs(), false, false);
                case 2 -> index = new Index(chunksParSource(), false, false);
                case 3 -> index = new Index(chunksParSource(), true, true);
                default -> { /* 4 et 5 : meme index que 3 */ }
            }
            boolean rerank = palier >= 4;
            boolean cloisonne = palier >= 5;
            System.out.printf("  Index : %d chunks%s, indexe en %.0f s%n", index.chunks.size(), repartition(index.chunks),
                    (System.currentTimeMillis() - t0) / 1000.0);

            // --- La recherche, mesuree sur les questions avec reponse ------------------
            Index vue = cloisonne ? index.pour(Role.DEVELOPPEUR) : index;
            List<Integer> rangs = new ArrayList<>();
            StringBuilder detail = new StringBuilder();
            for (Question q : avecReponse) {
                int rang = Recherche.rang(vue.chercher(q.texte(), rerank), q::attendu, 10);
                rangs.add(rang);
                detail.append(rang == 0 ? " -" : String.format("%2d", rang));
            }
            long h1 = rangs.stream().filter(r -> r == 1).count();
            long h3 = rangs.stream().filter(r -> r >= 1 && r <= 3).count();
            double mrr = rangs.stream().mapToDouble(r -> r == 0 ? 0 : 1.0 / r).average().orElse(0);
            System.out.printf("  Recherche (role developpeur) : rangs par question %s%n", detail);
            System.out.printf(Locale.ROOT, "  hit@1 %d/%d   hit@3 %d/%d   MRR %.3f%s%n", h1, rangs.size(), h3, rangs.size(), mrr,
                    cloisonne ? "   (les 3 questions confidentielles sont hors de la vue d'un developpeur : rang 0 attendu)" : "");
            String ligne = String.format(Locale.ROOT, "hit@1 %2d/%d  hit@3 %2d/%d  MRR %.3f", h1, rangs.size(), h3, rangs.size(), mrr);

            // --- La generation, aux paliers 1, 4 et 5 ---------------------------------
            if (GENERATION && (palier == 1 || palier == 4)) {
                int fondees = 0;
                int refus = 0;
                for (Question q : questions) {
                    Verdict v = generer(q, index.chercher(q.texte(), rerank).subList(0, K));
                    if (q.sansReponse()) {
                        refus += v.refus() ? 1 : 0;
                    } else {
                        fondees += v.fondee() ? 1 : 0;
                    }
                    System.out.printf("    Q%-2d %-6s %s%n", q.id(), q.sansReponse() ? (v.refus() ? "REFUS" : "invente")
                            : (v.fondee() ? "FONDEE" : "non"), Recherche.tronquer(v.reponse(), 95));
                }
                System.out.printf("  Generation : %d/%d reponses fondees (contenu attendu + citation qui le porte), %d/%d refus exacts%n",
                        fondees, avecReponse.size(), refus, questions.size() - avecReponse.size());
                ligne += String.format("  fondees %2d/%d  refus %d/%d", fondees, avecReponse.size(), refus, questions.size() - avecReponse.size());
            }
            if (palier == 5) {
                // Le test de fuite : les questions de la direction, posees par un developpeur puis par la direction.
                int fuites = 0;
                int refusDev = 0;
                int fondeesDirection = 0;
                List<Question> confidentielles = avecReponse.stream().filter(Question::confidentielle).toList();
                for (Question q : confidentielles) {
                    for (Role role : List.of(Role.DEVELOPPEUR, Role.DIRECTION)) {
                        List<Resultat> passages = index.pour(role).chercher(q.texte(), true).subList(0, K);
                        boolean fuite = passages.stream().anyMatch(r -> r.chunk().document().startsWith("direction-commerciale/"))
                                && role != Role.DIRECTION;
                        fuites += fuite ? 1 : 0;
                        if (GENERATION) {
                            Verdict v = generer(q, passages);
                            if (role == Role.DEVELOPPEUR) {
                                refusDev += v.refus() ? 1 : 0;
                            } else {
                                fondeesDirection += v.fondee() ? 1 : 0;
                            }
                            System.out.printf("    Q%-2d %-12s %-7s %s%n", q.id(), role, fuite ? "FUITE" : "ok",
                                    Recherche.tronquer(v.reponse(), 85));
                        } else {
                            System.out.printf("    Q%-2d %-12s %-7s passages : %s%n", q.id(), role, fuite ? "FUITE" : "ok",
                                    passages.stream().map(r -> r.chunk().document()).toList());
                        }
                    }
                }
                // Le test de fuite ECHOUE si le filtre ne prouve rien : sans filtre, un document de la direction
                // doit etre dans ce que la recherche VOIT (le sur-echantillon), pour chaque question confidentielle.
                final Index indexComplet = index;
                boolean sansFiltreVoit = true;
                for (Question q : confidentielles) {
                    List<Resultat> sansFiltre = indexComplet.chercher(q.texte(), true);
                    int rang = Recherche.rang(sansFiltre, c -> c.document().startsWith("direction-commerciale/"), SUR_ECHANTILLON);
                    System.out.printf("    Q%-2d sans filtre : premier document de la direction au rang %s sur %d%n", q.id(),
                            rang == 0 ? "-" : String.valueOf(rang), SUR_ECHANTILLON);
                    sansFiltreVoit &= rang > 0;
                }
                System.out.printf("  Cloisonnement : %d fuite(s) vers un developpeur sur %d questions ; sans filtre les documents de la direction remontent : %s%n",
                        fuites, confidentielles.size(), sansFiltreVoit ? "oui (le test prouve quelque chose)" : "NON (le test ne prouve rien)");
                if (GENERATION) {
                    System.out.printf("  Generation : developpeur -> %d/%d refus exacts ; direction -> %d/%d reponses fondees%n",
                            refusDev, confidentielles.size(), fondeesDirection, confidentielles.size());
                }
                ligne += String.format("  fuites %d/%d", fuites, confidentielles.size());
                if (fuites > 0 || !sansFiltreVoit) {
                    System.out.println("  ECHEC DU TEST DE CLOISONNEMENT");
                }
            }
            bilan.put("Palier " + palier, ligne);
            System.out.println();
        }

        System.out.println("=== Bilan, palier par palier ===");
        bilan.forEach((p, l) -> System.out.printf("  %-9s %s%n", p, l));
        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Chaque palier est une decision d'ingenierie, et chacune se lit dans un nombre sur le meme jeu de questions.");
        System.out.println("  Le cloisonnement n'est pas un palier de pertinence : c'est le seul dont l'echec est une faute.");
    }

    // -----------------------------------------------------------------
    // Les chunkings
    // -----------------------------------------------------------------

    /** Palier 1 : tout le corpus coupe a 500 caracteres, sans regarder le contenu. */
    static List<Chunk> chunksNaifs() throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        for (Path f : fichiers()) {
            String texte = Files.readString(f);
            String doc = document(f);
            for (int debut = 0, n = 1; debut < texte.length(); debut += 500, n++) {
                chunks.add(new Chunk(doc, "morceau " + n, texte.substring(debut, Math.min(texte.length(), debut + 500))));
            }
        }
        return chunks;
    }

    /** Palier 2 : chaque source decoupee comme elle est ecrite. */
    static List<Chunk> chunksParSource() throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        for (Path f : fichiers()) {
            String texte = Files.readString(f);
            String doc = document(f);
            if (f.toString().endsWith(".java")) {
                chunks.addAll(chunksJava(doc, texte));
            } else {
                // Confluence, tickets (un ticket = une section « ## n. »), docx, pptx (une diapo = un « # »), xlsx (une feuille = un « ## »).
                chunks.addAll(Recherche.sectionsMarkdown(doc, texte));
            }
        }
        return chunks;
    }

    private static final Pattern SIGNATURE = Pattern.compile("^\\s*(public|private|protected)\\b.*");

    /** Java : un membre (methode, constante) avec sa javadoc ; la section est « Classe · signature ». */
    static List<Chunk> chunksJava(String doc, String texte) {
        String classe = doc.substring(doc.lastIndexOf('/') + 1).replace(".java", "");
        List<Chunk> chunks = new ArrayList<>();
        for (String bloc : texte.split("(?m)(?=^ {4}/\\*\\*)")) {
            String b = bloc.strip();
            if (b.isEmpty()) {
                continue;
            }
            String signature = b.lines().filter(l -> SIGNATURE.matcher(l).matches()).findFirst()
                    .map(String::strip).orElse("en-tete");
            chunks.add(new Chunk(doc, classe + " · " + Recherche.tronquer(signature, 60), b));
        }
        return chunks;
    }

    // -----------------------------------------------------------------
    // La generation, et sa verification
    // -----------------------------------------------------------------

    static Verdict generer(Question q, List<Resultat> passages) {
        StringBuilder contexte = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            Chunk c = passages.get(i).chunk();
            contexte.append("[S").append(i + 1).append("] (").append(c.citation()).append(")\n").append(c.texte()).append("\n\n");
        }
        String reponse = Llm.chat("PASSAGES :\n\n" + contexte + "QUESTION : " + q.texte(), SYSTEME, 0.0).strip();
        return verifier(q, passages.stream().map(Resultat::chunk).toList(), reponse);
    }

    /** La verification, independante de qui a genere : la variante LangChain4j l'utilise telle quelle. */
    static Verdict verifier(Question q, List<Chunk> passages, String reponse) {
        boolean refus = reponse.contains(REFUS);
        boolean fondee = false;
        if (!q.sansReponse()) {
            // Fondee = la reponse contient un contenu attendu ET cite un passage qui porte ce contenu
            // (sous l'une de ses formes : « 0.06 » dans le tableau, « 6 % » dans la reponse).
            boolean contenu = q.reponses().stream().anyMatch(alt -> normaliser(reponse).contains(normaliser(alt)));
            boolean citation = false;
            Matcher m = Pattern.compile("\\[S([1-9])\\]").matcher(reponse);
            while (m.find()) {
                int i = Integer.parseInt(m.group(1)) - 1;
                if (i < passages.size() && q.reponses().stream()
                        .anyMatch(alt -> normaliser(passages.get(i).texte()).contains(normaliser(alt)))) {
                    citation = true;
                }
            }
            fondee = contenu && citation;
        }
        boolean fuite = passages.stream().anyMatch(c -> c.document().startsWith("direction-commerciale/"));
        return new Verdict(fondee, refus, fuite, reponse);
    }

    // -----------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------

    static List<Question> questions() throws IOException {
        JsonNode racine = new ObjectMapper().readTree(Donnees.chemin("tp-RAG/questions.json").toFile());
        List<Question> questions = new ArrayList<>();
        for (JsonNode n : racine) {
            questions.add(new Question(n.path("id").asInt(), n.path("question").asText(),
                    alternatives(n.path("source")), alternatives(n.path("section")), alternatives(n.path("reponse")),
                    n.path("acces").asText("tous")));
        }
        return questions;
    }

    private static List<String> alternatives(JsonNode n) {
        return n.isNull() || n.isMissingNode() ? List.of() : Arrays.stream(n.asText().split("\\|")).map(String::strip).toList();
    }

    /** Les fichiers indexables : Markdown et Java, jamais les binaires Office ni questions.json. */
    static List<Path> fichiers() throws IOException {
        Path racine = Donnees.chemin("tp-RAG");
        try (Stream<Path> s = Files.walk(racine)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> (p.toString().endsWith(".md") || p.toString().endsWith(".java")) && !p.getFileName().toString().equals("README.md"))
                    .sorted().toList();
        }
    }

    /** « confluence/02-regles-de-remise.md » : la source, puis le fichier. */
    static String document(Path f) {
        Path racine = Donnees.chemin("tp-RAG");
        return racine.relativize(f).toString().replace('\\', '/');
    }

    static String normaliser(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static String repartition(List<Chunk> chunks) {
        Map<String, Long> parSource = new LinkedHashMap<>();
        for (Chunk c : chunks) {
            parSource.merge(c.document().substring(0, c.document().indexOf('/')), 1L, Long::sum);
        }
        return " " + parSource;
    }

    static String titre(int palier) {
        return switch (palier) {
            case 1 -> "le socle naif : fixe 500, cosinus, top 3";
            case 2 -> "le chunking par source";
            case 3 -> "contexte dans le vecteur + hybride BM25 (RRF)";
            case 4 -> "reranking : top 8, juge pondere, top 3";
            default -> "cloisonnement par role, filtre AVANT la recherche";
        };
    }

    private static String env(String cle, String defaut) {
        String v = System.getenv(cle);
        return v == null || v.isBlank() ? defaut : v;
    }
}
