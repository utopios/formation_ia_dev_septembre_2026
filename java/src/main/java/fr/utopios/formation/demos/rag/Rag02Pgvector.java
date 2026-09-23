package fr.utopios.formation.demos.rag;

import fr.utopios.formation.demos.Donnees;

import fr.utopios.formation.commun.Llm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * RAG 01 (Module 5) : pgvector en direct.
 *
 * <p>Nous montrons, sans magie, le cycle complet d'un RAG minimal :</p>
 * <ol>
 *   <li>nous transformons des textes metier en vecteurs (nomic-embed-text) ;</li>
 *   <li>nous les stockons dans PostgreSQL/pgvector (colonne {@code vector(768)}) ;</li>
 *   <li>nous cherchons les plus proches d'une question par distance cosinus, EN SQL ;</li>
 *   <li>nous montrons le filtrage par service (confidentialite par cloisonnement).</li>
 * </ol>
 *
 * <p>Pre-requis : {@code podman compose up -d} dans {@code code/} (conteneur
 * {@code formation-pgvector} healthy) et Ollama demarre.</p>
 *
 * <p>Lancement :
 * {@code mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.rag.Rag02Pgvector"}</p>
 *
 * <p>La demo est idempotente : elle vide la table avant d'inserer son jeu de
 * donnees, pour pouvoir etre rejouee en salle autant de fois que voulu.</p>
 */
public class Rag02Pgvector {

    /** Identifiants volontairement simples : environnement de DEV uniquement. */
    private static final String URL = env("PGURL", "jdbc:postgresql://localhost:5432/formation");
    private static final String UTILISATEUR = "formation";
    private static final String MOT_DE_PASSE = "formation";

    /**
     * Jeu de donnees minimal : quelques « chunks » de trois services differents.
     *
     * <p>En salle, je lis ces phrases a voix haute : elles doivent etre
     * distinctes semantiquement pour que la recherche soit lisible.</p>
     */
    private record Document(String service, String contenu) {
    }

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

    /** Un resultat de recherche : le document trouve et sa proximite a la question. */
    private record Resultat(long id, String service, String contenu, double distance) {

        /** pgvector renvoie une DISTANCE cosinus ; la similarite vaut 1 - distance. */
        double similarite() {
            return 1.0 - distance;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== RAG 02 : pgvector en direct ===\n");

        try (Connection conn = DriverManager.getConnection(URL, UTILISATEUR, MOT_DE_PASSE)) {
            reinitTable(conn);
            insererCorpus(conn);

            // Recherche globale : la meilleure reponse doit venir du bon service,
            // alors que nous n'avons jamais mentionne le service dans la question.
            montrerRecherche(conn, "Comment demander des conges ?", null);
            montrerRecherche(conn, "Delai de paiement d'une facture fournisseur", null);
            montrerRecherche(conn, "Demande d'acces a une application", null);

            // Meme question, corpus restreint : nous ne voyons QUE ce service.
            // Point de securite : le cloisonnement se fait dans la REQUETE, pas
            // dans le prompt. C'est un controle d'acces, pas une politesse au LLM.
            System.out.println("--- Cloisonnement par service (meme question, corpus restreint) ---\n");
            montrerRecherche(conn, "Quelle est la regle sur les mots de passe ?", "Finance");
            montrerRecherche(conn, "Quelle est la regle sur les mots de passe ?", "IT");

        } catch (SQLException e) {
            System.out.println("Connexion pgvector impossible : " + e.getMessage());
            System.out.println("La base tourne-t-elle ? (cd code && podman compose up -d)");
            System.exit(1);
        }

        System.out.println("Fin de la demo 7.");
    }

    // ======================================================================
    // Ingestion
    // ======================================================================

    /** Vide la table pour repartir d'un etat propre (demo rejouable). */
    private static void reinitTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("TRUNCATE documents RESTART IDENTITY");
        }
    }

    private static void insererCorpus(Connection conn) throws SQLException {
        List<String> textes = CORPUS.stream().map(Document::contenu).toList();
        System.out.printf("[1] Calcul des embeddings de %d documents (nomic-embed-text)...%n",
                textes.size());

        // PIEGE CENTRAL DU RAG, ici cote INDEXATION : nomic-embed-text est un
        // modele ASYMETRIQUE. Il attend un prefixe qui annonce la nature du
        // texte. embedDocuments pose automatiquement "search_document: " sur
        // chaque texte du corpus. Plus bas, la question passera par embedQuery,
        // qui pose "search_query: ". Indexer et interroger avec le MEME prefixe
        // (ou sans prefixe) degrade nettement la pertinence : les deux textes
        // sont alors projetes dans le mauvais sous-espace l'un par rapport a
        // l'autre. C'est une erreur silencieuse : rien ne plante, la qualite
        // baisse simplement.
        List<double[]> vecteurs = Llm.embedDocuments(textes);
        System.out.printf("    -> %d vecteurs de dimension %d.%n",
                vecteurs.size(), vecteurs.get(0).length);

        System.out.println("[2] Insertion dans PostgreSQL/pgvector...");
        // Le type "vector" n'a pas de type JDBC dedie : nous passons le litteral
        // textuel "[a,b,c]" et laissons PostgreSQL le convertir par ::vector.
        String sql = """
                INSERT INTO documents (service, contenu, embedding, metadata)
                VALUES (?, ?, ?::vector, ?::jsonb)""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < CORPUS.size(); i++) {
                Document doc = CORPUS.get(i);
                ps.setString(1, doc.service());
                ps.setString(2, doc.contenu());
                ps.setString(3, Llm.versLitteralPgvector(vecteurs.get(i)));
                ps.setString(4, "{\"source\": \"demo-07\"}");
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM documents")) {
            rs.next();
            System.out.printf("    -> %d lignes en base.%n%n", rs.getLong(1));
        }
    }

    // ======================================================================
    // Recherche
    // ======================================================================

    private static void montrerRecherche(Connection conn, String question, String service)
            throws SQLException {
        String etiquette = service == null ? "" : " (filtre service = " + service + ")";
        System.out.printf("[?] Question%s : '%s'%n", etiquette, question);

        // PIEGE ASYMETRIQUE, ici cote INTERROGATION : embedQuery pose le prefixe
        // "search_query: ". C'est le pendant obligatoire du "search_document: "
        // utilise a l'indexation.
        double[] vecteurQuestion = Llm.embedQuery(question);

        for (Resultat r : rechercheSimilaire(conn, vecteurQuestion, 3, service)) {
            // Locale.ROOT : sans cela, une JVM en francais ecrit "0,737" au lieu
            // de "0.737", ce qui casse la lecture et le copier-coller des chiffres.
            System.out.printf(Locale.ROOT, "    %d. sim=%.3f  [%s]  %s%n",
                    r.id(), r.similarite(), r.service(), r.contenu());
        }
        System.out.println();
    }

    /**
     * Recherche les k documents les plus proches par distance cosinus.
     *
     * <p>L'operateur {@code <=>} de pgvector calcule la distance cosinus :
     * 0 signifie identique. Le filtre optionnel par service est une clause
     * {@code WHERE} ordinaire — c'est precisement ce qui en fait un controle
     * d'acces fiable, contrairement a une consigne donnee au modele.</p>
     */
    private static List<Resultat> rechercheSimilaire(
            Connection conn, double[] embedding, int k, String service) throws SQLException {

        String litteral = Llm.versLitteralPgvector(embedding);
        String filtre = service == null ? "" : "WHERE service = ?";
        String sql = """
                SELECT id, service, contenu,
                       embedding <=> ?::vector AS distance
                FROM documents
                %s
                ORDER BY embedding <=> ?::vector
                LIMIT ?""".formatted(filtre);

        List<Resultat> resultats = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Les parametres suivent l'ordre d'apparition des ? dans la requete :
            // 1) vecteur du SELECT, 2) service si filtre, 3) vecteur de l'ORDER BY, 4) k.
            int i = 1;
            ps.setString(i++, litteral);
            if (service != null) {
                ps.setString(i++, service);
            }
            ps.setString(i++, litteral);
            ps.setInt(i, k);

            try (ResultSet rs = ps.executeQuery()) {
                int rang = 1;
                while (rs.next()) {
                    resultats.add(new Resultat(
                            rang++,
                            rs.getString("service"),
                            rs.getString("contenu"),
                            rs.getDouble("distance")));
                }
            }
        }
        return resultats;
    }

    private static String env(String cle, String defaut) {
        String v = System.getenv(cle);
        return (v == null || v.isBlank()) ? defaut : v;
    }
}
