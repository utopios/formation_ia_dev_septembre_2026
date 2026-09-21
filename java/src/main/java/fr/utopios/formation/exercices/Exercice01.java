package fr.utopios.formation.exercices;

import com.fasterxml.jackson.databind.JsonNode;
import fr.utopios.formation.commun.Llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Exercice01{

    /** Nombre de tirages par prompt : sous 3, on ne voit pas la dispersion. */
    private static final int TIRAGES = 3;

    /** Temperature de test : 0.7 est le defaut applicatif courant. */
    private static final double TEMPERATURE = 0.7;

    /**
     * Le ticket de bug servant d'entree commune aux deux versions du prompt A.
     * Il contient volontairement du bruit (digression) et une information
     * manquante (aucun environnement precise) : c'est ce que le prompt robuste
     * doit savoir traiter.
     */
    private static final String TICKET = """
            [BUG-4172] Panier vide apres paiement
            Signale par : agence Lyon
            Franchement ca fait trois fois cette semaine, c'est penible.
            Quand un client paie par carte, la commande passe mais le panier
            reste affiche avec les articles. Il recommande parfois deux fois.
            Deux clients ont ete debites deux fois hier.
            """;

    /**
     * Un prompt et sa nature (naif ou robuste), pour les comparer a l'identique.
     * Un record : ce sont des donnees immuables, sans comportement.
     */
    private record Variante(String etiquette, String systeme, String prompt) {}

    public static void main(String[] args) {
        titre("Exercice 1 — Du prompt naif au prompt robuste");
        System.out.println("Modele : " + Llm.modeleChat()
                + " | temperature " + TEMPERATURE + " | " + TIRAGES + " tirages par prompt");

        // --- Partie 1 : la meme demande, deux redactions ---------------------
        var naif = new Variante(
                "NAIF",
                null,
                "Resume ce ticket.\n\n" + TICKET);

        // Les quatre briques : role, contexte, contraintes, format de sortie.
        var robuste = new Variante(
                "ROBUSTE",
                """
                Tu es un chef de produit qui prepare une reunion de triage.
                Tu ecris en francais, de facon factuelle, sans reprendre les
                digressions ni les jugements de valeur du redacteur.
                Si une information cle manque, tu la signales au lieu de l'inventer.
                """,
                """
                Voici un ticket de bug issu de notre outil de suivi. Le resume sera
                lu par des developpeurs et un chef de projet qui ne connaissent pas
                le ticket.

                Resume-le de facon actionnable, en 5 lignes maximum, en respectant
                EXACTEMENT ce format, une ligne par rubrique :
                Probleme : ...
                Impact : ...
                Reproduction : ...
                Information manquante : ...

                Ticket :
                \"\"\"
                """ + TICKET + "\"\"\"");

        var mesureNaif = mesurer(naif);
        var mesureRobuste = mesurer(robuste);

        // --- Partie 2 : la variante a sortie structuree -----------------------
        sousTitre("Variante a sortie structuree (JSON contraint)");
        JsonNode json = Llm.chatJson(
                """
                Extrais les informations de ce ticket de bug.
                Reponds par un objet JSON avec EXACTEMENT ces cles :
                  "titre" (string),
                  "severite" (une valeur parmi "faible", "moyenne", "elevee", "critique"),
                  "impact" (string),
                  "etapes_reproduction" (tableau de string),
                  "informations_manquantes" (tableau de string).

                Ticket :
                \"\"\"
                """ + TICKET + "\"\"\"",
                "Tu es un assistant d'extraction. Tu ne renvoies que du JSON, sans commentaire.");

        System.out.println("  titre    : " + json.path("titre").asText("(absent)"));
        System.out.println("  severite : " + json.path("severite").asText("(absent)"));
        System.out.println("  impact   : " + json.path("impact").asText("(absent)"));
        System.out.println("  etapes   : " + json.path("etapes_reproduction").size() + " element(s)");
        System.out.println("  manquant : " + json.path("informations_manquantes").size() + " element(s)");

        // La syntaxe JSON est garantie par Ollama ; le SCHEMA, jamais. Nous
        // validons donc nous-memes, exactement comme en production.
        List<String> defauts = validerSchema(json);
        System.out.println("\n  Validation applicative du schema : "
                + (defauts.isEmpty() ? "conforme" : "NON conforme"));
        defauts.forEach(d -> System.out.println("    - " + d));

        // --- Synthese ---------------------------------------------------------
        titre("Synthese mesuree");
        System.out.printf("  %-10s  reponses distinctes : %d/%d  | format respecte : %d/%d%n",
                "NAIF", mesureNaif.distinctes(), TIRAGES, mesureNaif.conformes(), TIRAGES);
        System.out.printf("  %-10s  reponses distinctes : %d/%d  | format respecte : %d/%d%n",
                "ROBUSTE", mesureRobuste.distinctes(), TIRAGES, mesureRobuste.conformes(), TIRAGES);
        System.out.println("""

                Ce que nous retenons : les deux prompts varient d'un tirage a l'autre,
                c'est la nature meme d'un decodage a temperature non nulle. Ce qui
                change, c'est la STRUCTURE : le prompt robuste impose un format que
                nous pouvons verifier par programme, donc exploiter en aval. Le prompt
                naif produit un texte que rien ne permet de parser.
                Le format de sortie est l'ajout a plus fort impact.""");
    }

    /** Resultat de la mesure de dispersion d'un prompt. */
    private record Mesure(int distinctes, int conformes) {}

    /**
     * Rejoue le meme prompt plusieurs fois et mesure deux choses :
     * le nombre de reponses textuellement distinctes (instabilite brute) et
     * le nombre de reponses respectant le format attendu (exploitabilite).
     */
    private static Mesure mesurer(Variante v) {
        sousTitre("Prompt " + v.etiquette());
        Set<String> vues = new LinkedHashSet<>();
        int conformes = 0;

        for (int i = 1; i <= TIRAGES; i++) {
            String reponse = Llm.chat(v.prompt(), v.systeme(), TEMPERATURE).strip();
            vues.add(reponse);
            boolean ok = respecteFormat(reponse);
            if (ok) {
                conformes++;
            }
            System.out.printf("  tirage %d | %d caracteres | format attendu : %s%n",
                    i, reponse.length(), ok ? "oui" : "non");
            System.out.println("    " + apercu(reponse));
        }
        return new Mesure(vues.size(), conformes);
    }

    /**
     * Le format impose au prompt robuste est verifiable par programme : c'est
     * tout l'interet. Nous cherchons les quatre rubriques demandees.
     */
    private static boolean respecteFormat(String reponse) {
        String bas = reponse.toLowerCase();
        boolean rubriqueProbleme = bas.contains("probleme :") || bas.contains("problème :");
        return rubriqueProbleme
                && bas.contains("impact")
                && bas.contains("reproduction")
                && (bas.contains("information manquante") || bas.contains("informations manquantes"));
    }

    /** Valide le SCHEMA de la sortie JSON : Ollama ne garantit que la syntaxe. */
    private static List<String> validerSchema(JsonNode json) {
        List<String> defauts = new ArrayList<>();
        Set<String> severites = Set.of("faible", "moyenne", "elevee", "critique");

        if (!json.path("titre").isTextual()) {
            defauts.add("titre absent ou non textuel");
        }
        String severite = json.path("severite").asText("");
        if (!severites.contains(severite)) {
            defauts.add("severite hors enumeration : \"" + severite + "\"");
        }
        if (!json.path("impact").isTextual()) {
            defauts.add("impact absent ou non textuel");
        }
        if (!json.path("etapes_reproduction").isArray()) {
            defauts.add("etapes_reproduction n'est pas un tableau");
        }
        if (!json.path("informations_manquantes").isArray()) {
            defauts.add("informations_manquantes n'est pas un tableau");
        }
        return defauts;
    }

    /** Premiere ligne utile de la reponse, tronquee, pour garder la trace lisible. */
    private static String apercu(String texte) {
        String ligne = texte.lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElse("(vide)");
        return ligne.length() <= 100 ? ligne : ligne.substring(0, 100) + "...";
    }

    private static void titre(String s) {
        System.out.println("\n" + "=".repeat(74));
        System.out.println(s);
        System.out.println("=".repeat(74));
    }

    private static void sousTitre(String s) {
        System.out.println("\n--- " + s + " " + "-".repeat(Math.max(0, 66 - s.length())));
    }
}
