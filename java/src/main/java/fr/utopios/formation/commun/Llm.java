package fr.utopios.formation.commun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Couche commune d'acces au LLM pour toutes les demos, exercices et TP.
 *
 * <p>Un LLM s'appelle avec une requete HTTP et du JSON : rien d'autre. On
 * n'utilise donc aucun SDK proprietaire, seulement {@link HttpClient}
 * (standard depuis Java 11) et Jackson pour le JSON. Vous pouvez lire
 * l'integralite de cette classe : il n'y a pas de magie cachee.</p>
 *
 * <p>Trois familles de methodes :</p>
 * <ul>
 *   <li><b>Chat</b> : {@link #chat(String)} pour une question simple,
 *       {@link #chat(List, double)} pour une conversation multi-tours,
 *       {@link #chatJson(String, String)} pour forcer une sortie JSON ;</li>
 *   <li><b>Embeddings</b> : {@link #embedDocument(String)} et
 *       {@link #embedQuery(String)} pour le RAG ;</li>
 *   <li><b>Outils</b> : {@link #cosine(double[], double[])} et les compteurs
 *       de tokens.</li>
 * </ul>
 *
 * <p><b>Piege a connaitre</b> : le modele d'embedding {@code nomic-embed-text}
 * est ASYMETRIQUE. Il attend un prefixe indiquant la nature du texte. Indexer
 * un corpus sans prefixe puis interroger avec prefixe (ou l'inverse) degrade
 * nettement la pertinence. Utilisez toujours {@link #embedDocument(String)}
 * pour le corpus et {@link #embedQuery(String)} pour la question.</p>
 *
 * <p>Configuration par variables d'environnement, sans toucher au code :
 * {@code OLLAMA_HOST}, {@code OLLAMA_CHAT_MODEL}, {@code OLLAMA_EMBED_MODEL}.</p>
 */
public final class Llm {

    /** URL du serveur Ollama (surchargeable par OLLAMA_HOST). */
    public static final String BASE_URL = env("OLLAMA_HOST", "http://localhost:11434");

    /** Modele de chat par defaut (surchargeable par OLLAMA_CHAT_MODEL). */
    public static final String MODELE_CHAT_DEFAUT = "llama3.2:1b";

    /** Modele d'embedding, dimension 768 (surchargeable par OLLAMA_EMBED_MODEL). */
    public static final String MODELE_EMBEDDING = env("OLLAMA_EMBED_MODEL", "nomic-embed-text");

    /** Dimension des vecteurs produits : doit correspondre a init.sql. */
    public static final int DIMENSION_EMBEDDING = 768;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static int derniersTokensPrompt = -1;
    private static int derniersTokensReponse = -1;
    private static long dernieresDureeMs = -1;

    private Llm() {
        // Classe utilitaire : pas d'instanciation.
    }

    private static String env(String cle, String defaut) {
        String v = System.getenv(cle);
        return (v == null || v.isBlank()) ? defaut : v.replaceAll("/+$", "");
    }

    /** Modele de chat effectif (variable d'environnement, sinon defaut). */
    public static String modeleChat() {
        return env("OLLAMA_CHAT_MODEL", MODELE_CHAT_DEFAUT);
    }

    // ======================================================================
    // Chat
    // ======================================================================

    /** Un message de conversation. Le role vaut "system", "user" ou "assistant". */
    public record Message(String role, String contenu) {

        public static Message systeme(String contenu) {
            return new Message("system", contenu);
        }

        public static Message utilisateur(String contenu) {
            return new Message("user", contenu);
        }

        public static Message assistant(String contenu) {
            return new Message("assistant", contenu);
        }
    }

    /**
     * Pose une question simple au modele (temperature 0.7, sans consigne systeme).
     *
     * @param prompt la question ou l'instruction
     * @return le texte de la reponse
     */
    public static String chat(String prompt) {
        return chat(List.of(Message.utilisateur(prompt)), 0.7);
    }

    /**
     * Pose une question avec une consigne systeme et une temperature choisie.
     *
     * @param prompt      la question de l'utilisateur
     * @param systeme     le role et les consignes donnes au modele, ou null
     * @param temperature 0.0 = deterministe et reproductible, 1.0 = creatif
     * @return le texte de la reponse
     */
    public static String chat(String prompt, String systeme, double temperature) {
        List<Message> messages = new ArrayList<>();
        if (systeme != null && !systeme.isBlank()) {
            messages.add(Message.systeme(systeme));
        }
        messages.add(Message.utilisateur(prompt));
        return chat(messages, temperature);
    }

    /**
     * Envoie une conversation complete (multi-tours) au modele.
     *
     * <p>C'est la forme la plus generale : un agent conversationnel rejoue
     * tout l'historique a chaque tour, car le modele n'a aucune memoire
     * entre deux appels. Cette absence de memoire explique la croissance du
     * cout d'une conversation longue.</p>
     *
     * @param messages    l'historique, du plus ancien au plus recent
     * @param temperature creativite du modele
     * @return le texte de la reponse
     */
    public static String chat(List<Message> messages, double temperature) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modeleChat());
        corps.put("stream", false);
        corps.putObject("options").put("temperature", temperature);
        remplirMessages(corps.putArray("messages"), messages);

        long debut = System.currentTimeMillis();
        JsonNode reponse = post("/api/chat", corps);
        dernieresDureeMs = System.currentTimeMillis() - debut;

        derniersTokensPrompt = reponse.path("prompt_eval_count").asInt(-1);
        derniersTokensReponse = reponse.path("eval_count").asInt(-1);
        System.err.printf("[llm] modele=%s tokens_prompt=%d tokens_reponse=%d duree=%d ms%n",
                modeleChat(), derniersTokensPrompt, derniersTokensReponse, dernieresDureeMs);

        return reponse.path("message").path("content").asText();
    }

    /**
     * Demande une reponse au format JSON et renvoie l'arbre Jackson analyse.
     *
     * <p>Ollama expose {@code "format": "json"}, qui contraint le decodage a
     * produire du JSON syntaxiquement valide. Attention : cela garantit la
     * SYNTAXE, jamais le SCHEMA. Un petit modele peut renvoyer un JSON valide
     * dont les champs sont absents ou mal nommes : il faut donc toujours
     * valider le contenu cote applicatif. C'est exactement ce que montre le
     * module sur les sorties structurees.</p>
     *
     * @param prompt  la demande, qui doit decrire le format attendu
     * @param systeme la consigne systeme, ou null
     * @return le JSON analyse
     * @throws IllegalStateException si la reponse n'est pas du JSON exploitable
     */
    public static JsonNode chatJson(String prompt, String systeme) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modeleChat());
        corps.put("stream", false);
        corps.put("format", "json");
        corps.putObject("options").put("temperature", 0.0);

        List<Message> messages = new ArrayList<>();
        if (systeme != null && !systeme.isBlank()) {
            messages.add(Message.systeme(systeme));
        }
        messages.add(Message.utilisateur(prompt));
        remplirMessages(corps.putArray("messages"), messages);

        JsonNode reponse = post("/api/chat", corps);
        derniersTokensPrompt = reponse.path("prompt_eval_count").asInt(-1);
        derniersTokensReponse = reponse.path("eval_count").asInt(-1);

        String texte = reponse.path("message").path("content").asText();
        try {
            return MAPPER.readTree(texte);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Le modele n'a pas renvoye de JSON exploitable : " + texte, e);
        }
    }

    /**
     * Genere une reponse en streaming, token par token.
     *
     * <p>Le streaming ne rend pas le modele plus rapide : il rend l'attente
     * visible. C'est un choix d'experience utilisateur, pas de performance.</p>
     *
     * @param prompt     la question
     * @param surFragment appele a chaque fragment recu
     * @return le texte complet reconstitue
     */
    public static String chatStream(String prompt, Consumer<String> surFragment) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modeleChat());
        corps.put("stream", true);
        remplirMessages(corps.putArray("messages"),
                List.of(Message.utilisateur(prompt)));

        StringBuilder complet = new StringBuilder();
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(corps), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> reponse =
                    HTTP.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            if (reponse.statusCode() != 200) {
                throw new IllegalStateException(
                        "Ollama a repondu " + reponse.statusCode() + " en streaming.");
            }
            // Ollama emet un objet JSON par ligne (NDJSON), pas un tableau.
            try (BufferedReader lecteur = new BufferedReader(
                    new InputStreamReader(reponse.body(), StandardCharsets.UTF_8))) {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    if (ligne.isBlank()) {
                        continue;
                    }
                    JsonNode bloc = MAPPER.readTree(ligne);
                    String fragment = bloc.path("message").path("content").asText("");
                    if (!fragment.isEmpty()) {
                        complet.append(fragment);
                        surFragment.accept(fragment);
                    }
                    if (bloc.path("done").asBoolean(false)) {
                        derniersTokensPrompt = bloc.path("prompt_eval_count").asInt(-1);
                        derniersTokensReponse = bloc.path("eval_count").asInt(-1);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Streaming interrompu : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Streaming interrompu", e);
        }
        return complet.toString();
    }

    private static void remplirMessages(ArrayNode cible, List<Message> messages) {
        for (Message m : messages) {
            cible.addObject().put("role", m.role()).put("content", m.contenu());
        }
    }

    // ======================================================================
    // Compteurs du dernier appel
    // ======================================================================

    /** Tokens consommes par le prompt lors du dernier appel (-1 si aucun). */
    public static int derniersTokensPrompt() {
        return derniersTokensPrompt;
    }

    /** Tokens generes lors du dernier appel (-1 si aucun). */
    public static int derniersTokensReponse() {
        return derniersTokensReponse;
    }

    /** Duree du dernier appel de chat, en millisecondes (-1 si aucun). */
    public static long dernieresDureeMs() {
        return dernieresDureeMs;
    }

    /**
     * Compte les tokens d'un texte SEUL, sans le gabarit de conversation.
     *
     * <p>Point de vigilance mesure en formation : en mode chat, Ollama
     * applique un gabarit (role, balises de conversation) qui ajoute une
     * vingtaine de tokens au prompt. Compter les tokens d'un texte via
     * {@code chat()} surestime donc nettement les textes courts. On passe
     * ici par {@code /api/generate} avec {@code raw:true}, qui desactive le
     * gabarit et ne compte que le texte lui-meme.</p>
     *
     * @param texte le texte a mesurer
     * @return le nombre de tokens du texte seul
     */
    public static int compterTokens(String texte) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modeleChat());
        corps.put("prompt", texte);
        corps.put("raw", true);
        corps.put("stream", false);
        corps.putObject("options").put("num_predict", 0);
        return post("/api/generate", corps).path("prompt_eval_count").asInt(-1);
    }

    // ======================================================================
    // Embeddings
    // ======================================================================

    /** Vectorise un texte sans prefixe. Pour le RAG, preferez les deux suivantes. */
    public static double[] embed(String texte) {
        return embed(texte, "");
    }

    /**
     * Vectorise un texte avec un prefixe de tache.
     *
     * @param texte  le texte a vectoriser
     * @param prefix "search_document: " ou "search_query: "
     * @return le vecteur, de dimension {@value #DIMENSION_EMBEDDING}
     */
    public static double[] embed(String texte, String prefix) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", MODELE_EMBEDDING);
        corps.put("prompt", prefix + texte);

        JsonNode vecteur = post("/api/embeddings", corps).path("embedding");
        if (vecteur.isMissingNode() || vecteur.isEmpty()) {
            throw new IllegalStateException(
                    "Embedding vide : le modele " + MODELE_EMBEDDING
                            + " est-il installe (ollama pull " + MODELE_EMBEDDING + ") ?");
        }
        double[] resultat = new double[vecteur.size()];
        for (int i = 0; i < vecteur.size(); i++) {
            resultat[i] = vecteur.get(i).asDouble();
        }
        return resultat;
    }

    /** Vectorise un DOCUMENT a indexer (prefixe {@code search_document: }). */
    public static double[] embedDocument(String texte) {
        return embed(texte, "search_document: ");
    }

    /** Vectorise une QUESTION utilisateur (prefixe {@code search_query: }). */
    public static double[] embedQuery(String texte) {
        return embed(texte, "search_query: ");
    }

    /** Vectorise plusieurs documents d'un coup (boucle simple, lisible). */
    public static List<double[]> embedDocuments(List<String> textes) {
        List<double[]> vecteurs = new ArrayList<>(textes.size());
        for (String t : textes) {
            vecteurs.add(embedDocument(t));
        }
        return vecteurs;
    }

    /**
     * Similarite cosinus entre deux vecteurs : -1 (oppose) a 1 (identique).
     *
     * <p>C'est la mesure de proximite semantique utilisee par toute recherche
     * vectorielle. pgvector calcule la DISTANCE cosinus avec l'operateur
     * {@code <=>} : distance = 1 - similarite.</p>
     */
    public static double cosine(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Dimensions differentes : " + a.length + " vs " + b.length);
        }
        double produit = 0, normeA = 0, normeB = 0;
        for (int i = 0; i < a.length; i++) {
            produit += a[i] * b[i];
            normeA += a[i] * a[i];
            normeB += b[i] * b[i];
        }
        double denominateur = Math.sqrt(normeA) * Math.sqrt(normeB);
        return denominateur == 0 ? 0 : produit / denominateur;
    }

    /** Represente un vecteur au format texte attendu par pgvector : {@code [a,b,c]}. */
    public static String versLitteralPgvector(double[] vecteur) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vecteur.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vecteur[i]);
        }
        return sb.append(']').toString();
    }

    // ======================================================================
    // Transport HTTP
    // ======================================================================

    private static JsonNode post(String chemin, ObjectNode corps) {
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + chemin))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(corps), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> reponse = HTTP.send(
                    requete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (reponse.statusCode() != 200) {
                throw new IllegalStateException("Ollama a repondu " + reponse.statusCode()
                        + " sur " + chemin + " : " + reponse.body());
            }
            return MAPPER.readTree(reponse.body());
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de joindre Ollama sur " + BASE_URL
                    + " (le service tourne-t-il ? 'ollama serve')", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel a Ollama interrompu", e);
        }
    }
}
