package fr.utopios.formation.demos.rag;

import fr.utopios.formation.demos.Donnees;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 02b (Module 5) : le MEME RAG, ecrit avec LangChain4j.
 *
 * <p>Nous venons d'ecrire la recherche vectorielle a la main
 * ({@link Rag02Pgvector}) : embeddings, litteral pgvector, SQL avec
 * {@code <=>}. Nous la reecrivons avec un framework. L'interet n'est pas de
 * choisir un camp, mais de voir ce que chaque approche vous donne et ce qu'elle
 * vous cache.</p>
 *
 * <p>Ce que LangChain4j apporte : un modele d'embedding, un magasin de vecteurs
 * et une recherche par score derriere une interface unique. Changer de magasin
 * ne change presque pas votre code — et la demo le PROUVE : les variantes 1 et
 * 2 tournent sur {@link InMemoryEmbeddingStore}, la variante 3 rejoue
 * exactement le meme code sur {@link PgVectorEmbeddingStore}, c'est-a-dire
 * sur la base pgvector de la formation, dans une table que le framework cree
 * lui-meme ({@code documents_langchain4j}). Seule la ligne qui construit le
 * magasin change. Si la base est injoignable, la variante 3 le dit et passe.</p>
 *
 * <p><b>Ce qu'il ne fait pas a votre place</b> : poser le prefixe asymetrique de
 * nomic-embed-text. {@code OllamaEmbeddingModel} envoie le texte BRUT, le meme
 * traitement pour le corpus et pour la question. Le modele ne distingue donc
 * plus un document d'une requete. C'est a vous de reposer le prefixe, ce que
 * fait la variante 2 ci-dessous.</p>
 *
 * <p><b>Et voici l'honnetete de la mesure</b>, qui est tout l'interet de faire
 * tourner la demo plutot que de reciter la regle : sur CE corpus, les prefixes
 * ne font pas gagner de pertinence. Les neuf phrases parlent de sujets tres
 * differents, le bon document sort premier dans les deux cas, et l'ecart avec le
 * meilleur distracteur est meme legerement plus confortable SANS prefixe. La
 * regle « toujours prefixer » reste juste — c'est ce que prescrit le modele, et
 * c'est ce qui compte sur un corpus dense ou des documents se ressemblent — mais
 * un corpus jouet ne suffit pas a la demontrer. Nous mesurons ici l'ECART entre
 * la bonne reponse et le meilleur distracteur, pas la similarite brute : une
 * similarite absolue plus haute ne vaut rien si tout le corpus monte avec
 * elle.</p>
 *
 * <p>Lancement :
 * {@code mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.rag.Rag02bPgvectorLangChain4j"}</p>
 */
public class Rag02bPgvectorLangChain4j {

    private record Document(String service, String contenu) {
    }

    /** Le meme corpus que la version faite main : les scores sont comparables. */
    private static final List<Document> CORPUS = List.of(
            new Document("RH", "La demande de conges se depose dans l'outil RH au moins deux semaines a l'avance."),
            new Document("RH", "Le teletravail est limite a trois jours par semaine, avec accord du manager."),
            new Document("RH", "La note de frais doit etre justifiee par un recu et validee par le responsable."),
            new Document("Finance", "Une facture fournisseur est reglee a 30 jours fin de mois apres reception."),
            new Document("Finance", "Le budget d'un projet est engage via un bon de commande avant tout achat."),
            new Document("Finance", "La cloture comptable mensuelle a lieu le cinquieme jour ouvre du mois suivant."),
            new Document("IT", "Une demande d'acces a une application passe par un ticket au support IT."),
            new Document("IT", "Les mots de passe sont renouveles tous les 90 jours et jamais partages."),
            new Document("IT", "La sauvegarde des serveurs de production est quotidienne et testee chaque mois."));

    /** Une question et l'indice du document qui doit sortir premier. */
    private record Cas(String question, int bonneReponse) {
    }

    private static final List<Cas> CAS = List.of(
            new Cas("Comment demander des conges ?", 0),
            new Cas("Delai de paiement d'une facture fournisseur", 3),
            new Cas("Demande d'acces a une application", 6));

    public static void main(String[] args) {
        System.out.println("=== RAG 02b : le meme RAG avec LangChain4j ===\n");

        OllamaEmbeddingModel modele = OllamaEmbeddingModel.builder()
                .baseUrl(Llm.BASE_URL)
                .modelName(Llm.MODELE_EMBEDDING)
                .timeout(Duration.ofMinutes(5))
                .build();

        // Variante 1 : le code « naturel » avec le framework, tel qu'on le trouve
        // dans la majorite des tutoriels. Le texte part tel quel.
        System.out.println("--- Variante 1 : texte brut (le reflexe habituel) ---");
        double ecartBrut = indexerEtChercher(modele, false, new InMemoryEmbeddingStore<>());

        // Variante 2 : nous reposons a la main le prefixe attendu par
        // nomic-embed-text. Le framework ne le fera pas a notre place.
        System.out.println("--- Variante 2 : prefixes search_document: / search_query: ---");
        double ecartPrefixe = indexerEtChercher(modele, true, new InMemoryEmbeddingStore<>());

        // Variante 3 : le MEME code, le magasin en moins — pgvector a la place de
        // la memoire. C'est la promesse du framework, et on la verifie : memes
        // scores, memes classements, table creee par LangChain4j.
        System.out.println("--- Variante 3 : le meme code sur pgvector (PgVectorEmbeddingStore) ---");
        EmbeddingStore<TextSegment> pgvector = magasinPgvector();
        if (pgvector != null) {
            double ecartPg = indexerEtChercher(modele, true, pgvector);
            System.out.printf(Locale.ROOT, "    [pgvector] ecart moyen = %+.3f, identique a la variante 2 : %s%n%n",
                    ecartPg, Math.abs(ecartPg - ecartPrefixe) < 0.001 ? "oui" : "NON");
        }

        System.out.printf(Locale.ROOT,
                "[Comparaison] ecart moyen bonne reponse / distracteur :"
                        + " sans prefixe = %+.3f, avec prefixe = %+.3f%n%n",
                ecartBrut, ecartPrefixe);

        System.out.println("""
                A retenir :
                  - Le framework industrialise le pipeline (magasin, recherche, interface
                    stable) ; il ne connait pas les particularites de VOTRE modele
                    d'embedding. Le prefixe asymetrique reste a votre charge.
                  - Sur ce corpus jouet, prefixer ne fait PAS gagner de pertinence : les
                    neuf phrases sont trop distinctes pour que la question soit difficile.
                    Nous gardons quand meme la regle du modele, et nous retenons surtout
                    la methode : on MESURE sur son propre corpus au lieu de croire.
                  - La version faite main vous apprend ce qui se passe ; la version
                    framework vous fait gagner du temps une fois que vous le savez.""");
    }

    /**
     * Le magasin pgvector de LangChain4j, sur la base de la formation (memes
     * identifiants que {@link Rag02Pgvector}, variable {@code PGURL}).
     *
     * <p>{@code createTable(true)} + {@code dropTableFirst(true)} : le framework
     * cree sa propre table ({@code embedding_id}, {@code embedding vector(768)},
     * {@code text}, {@code metadata}) et la vide a chaque lancement — la demo
     * reste rejouable. Ce n'est PAS la table {@code documents} de
     * {@link Rag02Pgvector} : deux schemas, deux facons de ranger la meme chose.
     * Verifiable en SQL : {@code SELECT count(*) FROM documents_langchain4j}.</p>
     *
     * @return le magasin, ou {@code null} si la base est injoignable
     */
    private static EmbeddingStore<TextSegment> magasinPgvector() {
        String url = System.getenv().getOrDefault("PGURL", "jdbc:postgresql://localhost:5432/formation");
        try (var ignored = DriverManager.getConnection(url, "formation", "formation")) {
            // La connexion JDBC directe sert de test de presence, avant de laisser
            // le framework ouvrir la sienne.
        } catch (SQLException e) {
            System.out.println("    base pgvector injoignable (" + url + ") : variante 3 non jouee — "
                    + Recherche.tronquer(e.getMessage(), 80) + "\n");
            return null;
        }
        URI u = URI.create(url.substring("jdbc:".length()));
        return PgVectorEmbeddingStore.builder()
                .host(u.getHost())
                .port(u.getPort() > 0 ? u.getPort() : 5432)
                .database(u.getPath().replaceFirst("^/", ""))
                .user("formation")
                .password("formation")
                .table("documents_langchain4j")
                .dimension(Llm.DIMENSION_EMBEDDING)
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    /**
     * Indexe le corpus puis interroge le magasin, avec ou sans prefixe de tache.
     *
     * @param prefixer {@code true} pour poser search_document: / search_query:
     * @return l'ecart moyen entre la bonne reponse et le meilleur distracteur
     */
    private static double indexerEtChercher(OllamaEmbeddingModel modele, boolean prefixer,
                                            EmbeddingStore<TextSegment> magasin) {

        // Indexation : un TextSegment par document. Le prefixe sert au calcul du
        // vecteur ; nous stockons le texte NON prefixe, car c'est lui que nous
        // rendrons a l'utilisateur.
        List<TextSegment> aVectoriser = CORPUS.stream()
                .map(d -> TextSegment.from(prefixer ? "search_document: " + d.contenu() : d.contenu()))
                .toList();
        List<Embedding> vecteurs = modele.embedAll(aVectoriser).content();

        for (int i = 0; i < vecteurs.size(); i++) {
            magasin.add(vecteurs.get(i), TextSegment.from(CORPUS.get(i).contenu()));
        }
        System.out.printf("    %d segments indexes, dimension %d.%n",
                vecteurs.size(), vecteurs.get(0).dimension());

        List<Double> ecarts = new ArrayList<>();
        for (Cas cas : CAS) {
            String texte = prefixer ? "search_query: " + cas.question() : cas.question();
            Embedding vecteurQuestion = modele
                    .embedAll(List.of(TextSegment.from(texte)))
                    .content()
                    .get(0);

            EmbeddingSearchRequest requete = EmbeddingSearchRequest.builder()
                    .queryEmbedding(vecteurQuestion)
                    .maxResults(CORPUS.size())
                    .build();
            List<EmbeddingMatch<TextSegment>> resultats = magasin.search(requete).matches();

            String attendu = CORPUS.get(cas.bonneReponse()).contenu();
            double simBonne = 0;
            double simDistracteur = -1;
            for (EmbeddingMatch<TextSegment> m : resultats) {
                // ATTENTION a la lecture du score : LangChain4j renvoie un score
                // normalise dans [0, 1] = (similarite cosinus + 1) / 2, alors que
                // pgvector nous donne la similarite brute dans [-1, 1]. Le
                // classement est le meme, les valeurs affichees non. Comparer des
                // scores entre deux outils sans verifier leur definition est une
                // source classique de fausses conclusions.
                double sim = 2 * m.score() - 1;
                if (m.embedded().text().equals(attendu)) {
                    simBonne = sim;
                } else if (sim > simDistracteur) {
                    simDistracteur = sim;
                }
            }
            boolean premier = resultats.get(0).embedded().text().equals(attendu);
            double ecart = simBonne - simDistracteur;
            ecarts.add(ecart);

            System.out.printf(Locale.ROOT,
                    "    '%s'%n      bonne=%.3f  distracteur=%.3f  ecart=%+.3f  top1=%s%n",
                    cas.question(), simBonne, simDistracteur, ecart, premier ? "OK" : "KO");
        }

        double moyenne = ecarts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        System.out.printf(Locale.ROOT, "    -> ecart moyen = %+.3f%n%n", moyenne);
        return moyenne;
    }
}
