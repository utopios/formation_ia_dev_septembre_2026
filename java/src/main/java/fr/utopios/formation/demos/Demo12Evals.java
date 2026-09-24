package fr.utopios.formation.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.utopios.formation.commun.Llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo 12 (Module 8) : une suite d'evals en action.
 *
 * <p>Objectif pedagogique : montrer qu'on TESTE un composant LLM comme on teste
 * du code. J'evalue ici un petit composant "classifieur de tickets de support"
 * avec quatre briques :</p>
 * <ol>
 *   <li>un jeu de tests (cas nominaux, cas limite, piege) ;</li>
 *   <li>un scoring AUTOMATIQUE deterministe (exact match sur la categorie) ;</li>
 *   <li>un LLM-as-judge (un second appel LLM note la justification de 1 a 5) ;</li>
 *   <li>un rapport agrege (taux de reussite, echecs detailles) exporte en JSON.</li>
 * </ol>
 *
 * <p>Le composant sous test renvoie du JSON structure
 * {@code {categorie, justification}}. Categories attendues : technique,
 * facturation, compte.</p>
 *
 * <p>Lancement :
 * {@code mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.Demo12Evals"}
 * Le rapport JSON est ecrit dans {@code data/demo-12/rapport_eval.json}.</p>
 */
public final class Demo12Evals {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Chemin du rapport, relatif au repertoire de lancement (racine du projet Maven). */
    private static final Path RAPPORT = Path.of("data", "demo-12", "rapport_eval.json");

    /** Un cas de test : un identifiant, un ticket, la categorie attendue. */
    record Cas(String id, String ticket, String attendu) {
    }

    /**
     * Jeu de tests : cas nominaux, cas limite, piege.
     *
     * <p>Le cas "limite-1" contient le mot "payer" alors que le probleme est
     * technique : c'est un piege lexical classique. Le cas "piege-1" n'a
     * aucune categorie evidente : je l'inclus volontairement pour montrer qu'un
     * jeu d'eval doit contenir des cas ou l'attendu lui-meme est discutable.</p>
     */
    static final List<Cas> JEU_DE_TESTS = List.of(
            new Cas("nominal-1",
                    "Je n'arrive plus a me connecter, la page affiche une erreur 500.",
                    "technique"),
            new Cas("nominal-2",
                    "Ma derniere facture me semble trop elevee, pouvez-vous verifier ?",
                    "facturation"),
            new Cas("nominal-3",
                    "Je veux changer l'adresse email de mon compte.",
                    "compte"),
            new Cas("limite-1",
                    "L'application plante quand je clique sur payer.",
                    "technique"),
            new Cas("piege-1",
                    "Bonjour, merci pour votre aide la derniere fois !",
                    "compte"));

    private Demo12Evals() {
    }

    /** Composant LLM a evaluer : classe un ticket et renvoie un JSON structure. */
    static JsonNode composantSousTest(String ticket) {
        String systeme = "Tu es un classifieur de tickets de support. Categories autorisees : "
                + "technique, facturation, compte. Reponds STRICTEMENT en JSON, sur une "
                + "ligne : {\"categorie\": \"...\", \"justification\": \"...\"}. Aucune autre "
                + "sortie.";
        // format:json d'Ollama garantit la SYNTAXE, jamais le SCHEMA : c'est
        // pour cela que l'extraction reste tolerante juste en dessous.
        try {
            return Llm.chatJson("Ticket : " + ticket, systeme);
        } catch (RuntimeException e) {
            return extraireJson(String.valueOf(e.getMessage()));
        }
    }

    /**
     * Extrait un objet JSON d'une reponse LLM, de facon tolerante.
     *
     * <p>Un petit modele produit souvent du JSON approximatif : bavardage
     * autour, objet imbrique, guillemets non echappes. Ce parsing tolerant fait
     * partie du HARNESS d'eval, PAS du composant teste : je ne veux pas qu'un
     * point-virgule fausse la mesure de la vraie qualite.</p>
     */
    static ObjectNode extraireJson(String texte) {
        // 1. Tenter les blocs {...}, du plus court au plus long.
        List<String> candidats = new ArrayList<>();
        Matcher court = Pattern.compile("\\{.*?\\}", Pattern.DOTALL).matcher(texte);
        while (court.find()) {
            candidats.add(court.group());
        }
        Matcher longuet = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(texte);
        while (longuet.find()) {
            candidats.add(longuet.group());
        }
        for (String bloc : candidats) {
            try {
                JsonNode noeud = MAPPER.readTree(bloc);
                if (noeud.isObject() && noeud.has("categorie")) {
                    ObjectNode objet = (ObjectNode) noeud;
                    if (!objet.has("justification")) {
                        objet.put("justification", "");
                    }
                    return objet;
                }
            } catch (IOException ignore) {
                // Bloc non parsable : on essaie le suivant.
            }
        }

        // 2. Repli regex : aller chercher les valeurs meme si le JSON est casse.
        ObjectNode repli = MAPPER.createObjectNode();
        String categorie = premierGroupe(texte, "\"?categorie\"?\\s*[:=]\\s*\"?([a-zA-Z]+)");
        String note = premierGroupe(texte, "\"?note\"?\\s*[:=]\\s*\"?(\\d)");
        String justification = premierGroupe(texte,
                "\"?justification\"?\\s*[:=]\\s*\"?([^\"}\\n]{3,})");
        String commentaire = premierGroupe(texte,
                "\"?commentaire\"?\\s*[:=]\\s*\"?([^\"}\\n]{3,})");
        if (categorie != null || note != null) {
            repli.put("categorie", categorie != null ? categorie : "?");
            repli.put("justification", justification != null ? justification.trim() : "");
            repli.put("note", note != null ? note : "");
            repli.put("commentaire", commentaire != null ? commentaire.trim() : "");
            return repli;
        }
        repli.put("categorie", "?");
        repli.put("justification", texte.strip().length() > 200
                ? texte.strip().substring(0, 200) : texte.strip());
        repli.put("_parse", "echec");
        return repli;
    }

    private static String premierGroupe(String texte, String regex) {
        Matcher m = Pattern.compile(regex).matcher(texte);
        return m.find() ? m.group(1) : null;
    }

    /** Scoring automatique deterministe : la categorie correspond-elle ? */
    static boolean scoreExact(String attendu, String obtenu) {
        return attendu.strip().equalsIgnoreCase(String.valueOf(obtenu).strip());
    }

    /** Note attribuee par le juge, avec son commentaire. */
    record Jugement(int note, String commentaire) {
    }

    /**
     * Un second LLM note la QUALITE de la justification (1 a 5).
     *
     * <p>Point pedagogique : le LLM-as-judge evalue ce qu'un exact-match ne
     * capte pas (pertinence, clarte). Je lui impose une sortie structuree, elle
     * aussi soumise au parsing tolerant. Et je rappelle en salle que le juge
     * est faillible : sur un vrai projet, on le calibre et on l'audite.</p>
     */
    static Jugement llmAsJudge(String ticket, String categorie, String justification) {
        String systeme = "Tu es un evaluateur qualite. On te donne un ticket, une categorie "
                + "predite et sa justification. Note de 1 (mauvaise) a 5 (excellente) "
                + "la pertinence de la justification vis-a-vis du ticket. Reponds en "
                + "JSON sur une ligne : {\"note\": <1-5>, \"commentaire\": \"...\"}.";
        String demande = "Ticket : " + ticket + "\nCategorie predite : " + categorie
                + "\nJustification : " + justification;

        JsonNode objet;
        try {
            objet = Llm.chatJson(demande, systeme);
        } catch (RuntimeException e) {
            objet = extraireJson(String.valueOf(e.getMessage()));
        }

        int note = 0;
        Matcher chiffre = Pattern.compile("\\d").matcher(objet.path("note").asText(""));
        if (chiffre.find()) {
            note = Integer.parseInt(chiffre.group());
        }
        return new Jugement(Math.max(0, Math.min(5, note)),
                objet.path("commentaire").asText(""));
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== Demo 12 : suite d'evals en action ===\n");

        ArrayNode details = MAPPER.createArrayNode();
        int reussitesExact = 0;
        int totalNoteJuge = 0;

        for (Cas cas : JEU_DE_TESTS) {
            System.out.println("[" + cas.id() + "] " + cas.ticket());
            JsonNode sortie = composantSousTest(cas.ticket());
            String categorie = sortie.path("categorie").asText("?");
            String justification = sortie.path("justification").asText("");

            boolean ok = scoreExact(cas.attendu(), categorie);
            if (ok) {
                reussitesExact++;
            }
            Jugement juge = llmAsJudge(cas.ticket(), categorie, justification);
            totalNoteJuge += juge.note();

            System.out.printf("    predit='%s' attendu='%s' -> %s (exact match) | juge=%d/5%n",
                    categorie, cas.attendu(), ok ? "PASS" : "FAIL", juge.note());
            System.out.println("    justification : " + tronquer(justification, 90));
            System.out.println();

            ObjectNode detail = details.addObject();
            detail.put("id", cas.id());
            detail.put("ticket", cas.ticket());
            detail.put("attendu", cas.attendu());
            detail.put("predit", categorie);
            detail.put("exact_match", ok);
            detail.put("note_juge", juge.note());
            detail.put("commentaire_juge", juge.commentaire());
        }

        int n = JEU_DE_TESTS.size();
        double taux = (double) reussitesExact / n;
        double noteMoyenne = (double) totalNoteJuge / n;

        ObjectNode rapport = MAPPER.createObjectNode();
        rapport.put("nb_cas", n);
        rapport.put("reussites_exact_match", reussitesExact);
        rapport.put("taux_exact_match", Math.round(taux * 1000) / 1000.0);
        rapport.put("note_moyenne_juge_sur_5", Math.round(noteMoyenne * 100) / 100.0);
        rapport.put("modele_evalue", Llm.modeleChat());
        rapport.set("details", details);

        Files.createDirectories(RAPPORT.getParent());
        Files.writeString(RAPPORT, MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(rapport));

        System.out.println("=== Rapport agrege ===");
        System.out.printf("  Cas evalues            : %d%n", n);
        System.out.printf("  Exact match            : %d/%d  (taux %.0f%%)%n",
                reussitesExact, n, taux * 100);
        System.out.printf("  Note moyenne LLM-juge  : %.2f/5%n", noteMoyenne);
        System.out.println("  Rapport JSON ecrit     : " + RAPPORT.toAbsolutePath());
        System.out.println();
        System.out.println("A souligner : deux metriques COMPLEMENTAIRES. L'exact-match verifie");
        System.out.println("la decision ; le LLM-as-judge evalue la qualite du raisonnement.");
        System.out.println("Un LLM local 1b est instable : c'est justement pourquoi on l'EVALUE.");
    }

    private static String tronquer(String texte, int max) {
        String propre = texte.replaceAll("\\s+", " ").trim();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }
}
