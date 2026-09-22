package fr.utopios.formation.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


public final class DemoBaseVsInstruct {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String OLLAMA_HOST = env("OLLAMA_HOST", "http://localhost:11434");
    private static final String MODELE_PETIT = env("DEMO14_MODELE", "llama3.2:1b");
    private static final int NUM_PREDICT = Integer.parseInt(env("DEMO14_NUM_PREDICT", "80"));

    /**
     * Prompt commun aux deux modes : une consigne. En completion brute, le
     * modele tend a CONTINUER ce texte ; en mode chat, il y REPOND.
     */
    private static final String PROMPT =
            "Explique en une phrase ce qu'est une base de donnees vectorielle.";

    /**
     * Prompt de la comparaison de modeles : une consigne stricte, revelatrice
     * de la difference de suivi d'instruction entre tailles de modeles.
     */
    private static final String PROMPT_COMPARAISON =
            "Donne exactement 3 risques de securite d'un agent IA qui execute des "
                    + "commandes shell, en 3 lignes numerotees, sans introduction ni conclusion.";

    private Demo14BaseVsInstruct() {
    }

    private static String env(String cle, String defaut) {
        String v = System.getenv(cle);
        return (v == null || v.isBlank()) ? defaut : v.replaceAll("/+$", "");
    }

    /** Completion brute : /api/generate avec raw=true, aucun template de chat. */
    static String genererBrut(String modele, String prompt) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modele);
        corps.put("prompt", prompt);
        corps.put("raw", true);
        corps.put("stream", false);
        corps.putObject("options").put("temperature", 0).put("num_predict", NUM_PREDICT);
        return post("/api/generate", corps).path("response").asText("");
    }

    /** Mode assistant : /api/chat, template de chat instruct applique par Ollama. */
    static String chat(String modele, String prompt) {
        ObjectNode corps = MAPPER.createObjectNode();
        corps.put("model", modele);
        corps.put("stream", false);
        corps.putObject("options").put("temperature", 0).put("num_predict", NUM_PREDICT);
        corps.putArray("messages").addObject().put("role", "user").put("content", prompt);
        return post("/api/chat", corps).path("message").path("content").asText("");
    }

    /** Liste les modeles locaux, en ecartant les modeles d'embeddings. */
    static List<String> modelesChatDisponibles() {
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_HOST + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> reponse = HTTP.send(requete,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode modeles = MAPPER.readTree(reponse.body()).path("models");
            List<String> noms = new ArrayList<>();
            for (JsonNode m : modeles) {
                String nom = m.path("name").asText("");
                if (!nom.toLowerCase().contains("embed")) {
                    noms.add(nom);
                }
            }
            return noms;
        } catch (IOException e) {
            throw new IllegalStateException("Ollama injoignable sur " + OLLAMA_HOST
                    + " (lancez 'ollama serve')", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel interrompu", e);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Demo 14 : modele de base vs modele instruct ===\n");

        // ---------------- Partie A : completion brute vs chat ----------------
        System.out.println("Prompt commun : '" + PROMPT + "'\n");

        System.out.println("--- A1. 'Mode base' : /api/generate, raw=true (" + MODELE_PETIT + ") ---");
        System.out.println("(le prompt part sans template de chat : le modele CONTINUE le texte)\n");
        System.out.println(ouVide(genererBrut(MODELE_PETIT, PROMPT).strip()));

        System.out.println("\n--- A2. Mode instruct : /api/chat (" + MODELE_PETIT + ") ---");
        System.out.println("(template de chat applique : le modele REPOND a la consigne)\n");
        System.out.println(ouVide(chat(MODELE_PETIT, PROMPT).strip()));

        System.out.println("\nNUANCE : llama3.2:1b est deja fine-tune instruct ; raw=true retire");
        System.out.println("seulement le template de chat. Un VRAI modele de base (Llama pre-");
        System.out.println("entraine seul) derive davantage : il ne 'sait' que continuer du texte.");

        // ---------------- Partie B : comparaison de modeles ------------------
        System.out.println("\n=== Comparaison de modeles (meme prompt, /api/chat) ===\n");
        System.out.println("Prompt : '" + PROMPT_COMPARAISON + "'\n");

        List<String> disponibles = modelesChatDisponibles();
        List<String> autres = disponibles.stream()
                .filter(m -> !m.startsWith(MODELE_PETIT))
                .toList();

        List<String> aComparer = new ArrayList<>();
        aComparer.add(MODELE_PETIT);
        if (!autres.isEmpty()) {
            aComparer.add(autres.get(0));
        }

        for (String modele : aComparer) {
            System.out.println("--- " + modele + " ---");
            long debut = System.currentTimeMillis();
            String reponse = chat(modele, PROMPT_COMPARAISON).strip();
            long duree = System.currentTimeMillis() - debut;
            System.out.println(ouVide(reponse));
            // La latence fait partie du critere de choix : je l'affiche pour
            // que le compromis qualite / cout soit visible, pas theorique.
            System.out.println("(latence : " + duree + " ms)");
            System.out.println();
        }

        if (autres.isEmpty()) {
            System.out.println("Un seul modele de chat est present. Pour la comparaison, installer");
            System.out.println("un second modele (optionnel) :  ollama pull llama3.2:3b");
            System.out.println("puis relancer cette demo.");
        } else {
            System.out.println("A observer : suivi de la consigne (exactement 3 lignes ? pas");
            System.out.println("d'introduction ?), justesse et concision. La taille du modele se");
            System.out.println("paie en latence et en memoire : le bon modele est le plus PETIT");
            System.out.println("qui fait le travail -- c'est le critere de choix par tache (agents).");
        }
    }

    private static String ouVide(String texte) {
        return texte.isEmpty() ? "(sortie vide)" : texte;
    }

    private static JsonNode post(String chemin, ObjectNode corps) {
        try {
            HttpRequest requete = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_HOST + chemin))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(corps), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> reponse = HTTP.send(requete,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (reponse.statusCode() != 200) {
                throw new IllegalStateException("Ollama a repondu " + reponse.statusCode()
                        + " sur " + chemin + " : " + reponse.body());
            }
            return MAPPER.readTree(reponse.body());
        } catch (IOException e) {
            throw new IllegalStateException("Ollama injoignable sur " + OLLAMA_HOST
                    + " (lancez 'ollama serve')", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel interrompu", e);
        }
    }
}
