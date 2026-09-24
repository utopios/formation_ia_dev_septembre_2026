package fr.utopios.formation.tp;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.tp.TpRagSolution.Question;
import fr.utopios.formation.tp.TpRagSolution.Role;
import fr.utopios.formation.tp.TpRagSolution.Verdict;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * TP RAG, palier 6 (bonus) : le MEME assistant, ecrit avec LangChain4j.
 *
 * <p>Ce que le framework prend en charge ici : le segment et ses metadonnees
 * ({@link TextSegment}, {@link Metadata}), le modele d'embedding
 * ({@link OllamaEmbeddingModel}), le magasin et sa recherche avec
 * <b>filtre de metadonnees</b> ({@link InMemoryEmbeddingStore},
 * {@link EmbeddingSearchRequest}), et la generation par un service declaratif
 * ({@link AiServices}, prompt en gabarit).</p>
 *
 * <p>Ce qui reste a nous, et que la mesure verifie avec exactement le meme
 * harnais que {@link TpRagSolution} :</p>
 * <ul>
 *   <li>le decoupage par source : LangChain4j 1.0 n'a pas de splitteur par
 *       titres Markdown ni par methode Java — on garde le notre, et on
 *       l'emballe dans des segments avec metadonnees ;</li>
 *   <li>le <b>prefixe asymetrique</b> de nomic-embed-text : le modele
 *       LangChain4j envoie le texte brut, il faut poser
 *       {@code search_document:} / {@code search_query:} soi-meme (le piege de
 *       la demo RAG 02b) ;</li>
 *   <li>le cloisonnement : un {@link Filter} sur la metadonnee {@code source},
 *       construit a partir du role — le framework l'applique AVANT la
 *       similarite, comme notre {@code Index.pour(role)} ;</li>
 *   <li>la verification des reponses : citations, refus, fuites.</li>
 * </ul>
 *
 * <p>Pas de BM25 ni de reranking ici : l'hybride n'est pas dans le magasin en
 * memoire de LangChain4j 1.0. C'est le point de comparaison avec le palier 3.</p>
 *
 * <p>Lancement :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.tp.TpRagLangChain4j"}</p>
 */
public final class TpRagLangChain4j {

    /** Le service de generation : LangChain4j assemble le prompt a partir du gabarit. */
    interface Assistant {
        @SystemMessage("""
                Tu reponds UNIQUEMENT a partir des passages fournis, numerotes [S1], [S2], [S3].
                Apres chaque fait que tu avances, cite le passage entre crochets, par exemple [S2].
                Si les passages ne contiennent pas l'information demandee, reponds exactement :
                « Je ne trouve pas cette information dans les documents fournis. » et rien d'autre.
                Reponds en francais, en deux phrases au plus.""")
        @UserMessage("PASSAGES :\n\n{{passages}}\nQUESTION : {{question}}")
        String repondre(@V("passages") String passages, @V("question") String question);
    }

    private static final int K = 3;
    private static final boolean GENERATION = !"non".equalsIgnoreCase(System.getenv().getOrDefault("GENERATION", "oui"));

    private TpRagLangChain4j() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== TP RAG, palier 6 : le meme assistant avec LangChain4j ===");
        List<Question> questions = TpRagSolution.questions();
        List<Question> avecReponse = questions.stream().filter(q -> !q.sansReponse()).toList();

        EmbeddingModel embeddings = OllamaEmbeddingModel.builder()
                .baseUrl(Llm.BASE_URL).modelName(Llm.MODELE_EMBEDDING).timeout(Duration.ofMinutes(5)).build();
        OllamaChatModel chat = OllamaChatModel.builder()
                .baseUrl(Llm.BASE_URL).modelName(Llm.modeleChat()).temperature(0.0).timeout(Duration.ofMinutes(5)).build();
        Assistant assistant = AiServices.builder(Assistant.class).chatModel(chat).build();

        // ---- Ingestion : notre decoupage, les metadonnees du framework -------------
        long t0 = System.currentTimeMillis();
        List<Chunk> chunks = TpRagSolution.chunksParSource();
        EmbeddingStore<TextSegment> magasin = new InMemoryEmbeddingStore<>();
        for (Chunk c : chunks) {
            String source = c.document().substring(0, c.document().indexOf('/'));
            Metadata meta = new Metadata().put("source", source).put("document", c.document()).put("section", c.section());
            TextSegment segment = TextSegment.from(c.texte(), meta);
            // Le contexte dans le vecteur (palier 3) ET le prefixe document de nomic : les deux sont a nous.
            Embedding vecteur = embeddings.embed("search_document: Source : " + c.document() + "\nSection : " + c.section()
                    + "\n\n" + c.texte()).content();
            magasin.add(vecteur, segment);
        }
        System.out.printf("  Index : %d segments avec metadonnees (source, document, section), %.0f s%n%n",
                chunks.size(), (System.currentTimeMillis() - t0) / 1000.0);

        // ---- Recherche mesuree, vue developpeur ---------------------------------------
        List<Integer> rangs = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        for (Question q : avecReponse) {
            List<Chunk> top = chercher(magasin, embeddings, q.texte(), Role.DEVELOPPEUR, 10);
            int rang = 0;
            for (int i = 0; i < top.size(); i++) {
                if (q.attendu(top.get(i))) {
                    rang = i + 1;
                    break;
                }
            }
            rangs.add(rang);
            detail.append(rang == 0 ? " -" : String.format("%2d", rang));
        }
        long h1 = rangs.stream().filter(r -> r == 1).count();
        long h3 = rangs.stream().filter(r -> r >= 1 && r <= 3).count();
        double mrr = rangs.stream().mapToDouble(r -> r == 0 ? 0 : 1.0 / r).average().orElse(0);
        System.out.println("  Recherche (role developpeur, filtre de metadonnees) : rangs " + detail);
        System.out.printf(Locale.ROOT, "  hit@1 %d/%d   hit@3 %d/%d   MRR %.3f   (comparer au palier 3 : contexte sans BM25, et au palier 5)%n%n",
                h1, rangs.size(), h3, rangs.size(), mrr);

        // ---- Generation et verification, meme harnais ---------------------------------
        if (GENERATION) {
            int fondees = 0;
            int refus = 0;
            for (Question q : questions) {
                Role role = q.confidentielle() ? Role.DIRECTION : Role.DEVELOPPEUR;
                List<Chunk> passages = chercher(magasin, embeddings, q.texte(), role, K);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < passages.size(); i++) {
                    sb.append("[S").append(i + 1).append("] (").append(passages.get(i).citation()).append(")\n")
                            .append(passages.get(i).texte()).append("\n\n");
                }
                String reponse = assistant.repondre(sb.toString(), q.texte()).strip();
                Verdict v = TpRagSolution.verifier(q, passages, reponse);
                if (q.sansReponse()) {
                    refus += v.refus() ? 1 : 0;
                } else {
                    fondees += v.fondee() ? 1 : 0;
                }
                System.out.printf("    Q%-2d %-6s %s%n", q.id(), q.sansReponse() ? (v.refus() ? "REFUS" : "invente")
                        : (v.fondee() ? "FONDEE" : "non"), Recherche.tronquer(v.reponse(), 95));
            }
            System.out.printf("%n  Generation : %d/%d reponses fondees, %d/%d refus exacts%n", fondees, avecReponse.size(),
                    refus, questions.size() - avecReponse.size());
        }

        // ---- Le test de fuite, avec le filtre du framework ----------------------------
        System.out.println();
        int fuites = 0;
        boolean sansFiltreVoit = true;
        for (Question q : avecReponse.stream().filter(Question::confidentielle).toList()) {
            List<Chunk> dev = chercher(magasin, embeddings, q.texte(), Role.DEVELOPPEUR, K);
            boolean fuite = dev.stream().anyMatch(c -> c.document().startsWith("direction-commerciale/"));
            fuites += fuite ? 1 : 0;
            List<Chunk> sansFiltre = chercher(magasin, embeddings, q.texte(), null, 8);
            int rang = 0;
            for (int i = 0; i < sansFiltre.size(); i++) {
                if (sansFiltre.get(i).document().startsWith("direction-commerciale/")) {
                    rang = i + 1;
                    break;
                }
            }
            sansFiltreVoit &= rang > 0;
            System.out.printf("    Q%-2d developpeur : %-5s   sans filtre : premier document de la direction au rang %s sur 8%n",
                    q.id(), fuite ? "FUITE" : "ok", rang == 0 ? "-" : String.valueOf(rang));
        }
        System.out.printf("  Cloisonnement : %d fuite(s) ; sans filtre les documents de la direction remontent : %s%n",
                fuites, sansFiltreVoit ? "oui" : "NON (le test ne prouve rien)");
        if (fuites > 0 || !sansFiltreVoit) {
            System.out.println("  ECHEC DU TEST DE CLOISONNEMENT");
        }

        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Le framework a pris le magasin, la recherche et le filtre de metadonnees, le prompt en gabarit.");
        System.out.println("  Le decoupage par source, le prefixe nomic, la regle « quel role voit quelle source », la");
        System.out.println("  verification des citations et le test de fuite sont restes a nous — et ce sont eux qu'on mesure.");
    }

    /** La recherche : vecteur de requete (prefixe requete), filtre par role, top k — puis retour a nos chunks. */
    static List<Chunk> chercher(EmbeddingStore<TextSegment> magasin, EmbeddingModel embeddings, String question, Role role, int k) {
        Embedding requete = embeddings.embed("search_query: " + question).content();
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder req = EmbeddingSearchRequest.builder().queryEmbedding(requete).maxResults(k);
        if (role != null) {
            List<String> sources = TpRagSolution.SOURCES.stream().filter(s -> s.acces().contains(role)).map(s -> s.nom()).toList();
            Filter filtre = metadataKey("source").isIn(sources);
            req.filter(filtre);
        }
        List<Chunk> resultat = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> m : magasin.search(req.build()).matches()) {
            Metadata meta = m.embedded().metadata();
            resultat.add(new Chunk(meta.getString("document"), meta.getString("section"), m.embedded().text()));
        }
        return resultat;
    }
}
