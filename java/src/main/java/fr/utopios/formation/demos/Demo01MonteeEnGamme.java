package fr.utopios.formation.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo 1 (Module 1) : montee en gamme d'un prompt, effet mesure.
 *
 * <p>Nous resolvons la MEME tache (extraire des informations d'un avis client
 * et produire un JSON) avec quatre niveaux de prompt :</p>
 * <ol>
 *   <li>NAIF : une phrase vague ;</li>
 *   <li>STRUCTURE : role + contexte + contraintes + format de sortie ;</li>
 *   <li>FEW-SHOT : structure + un exemple entree/sortie ;</li>
 *   <li>GABARIT : gabarit d'equipe parametre + schema JSON explicite.</li>
 * </ol>
 *
 * <p>L'effet mesure : nous tentons d'analyser la sortie en JSON et nous
 * verifions la presence des champs attendus. Le score vaut 0 si aucun JSON
 * n'est exploitable, sinon la fraction de champs attendus effectivement
 * presents et renseignes.</p>
 *
 * <p>Lancement :
 * {@code cd java && mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.Demo01MonteeEnGamme"}</p>
 */
public final class Demo01MonteeEnGamme {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AVIS = """
            J'ai commande le casque le 3 mars. Livraison rapide mais le micro gresille \
            des le premier appel. Le service client a repondu en une heure, tres pro. \
            Je recommande malgre le souci technique.""";

    private static final List<String> CHAMPS_ATTENDUS =
            List.of("sentiment", "produit", "probleme", "point_positif");

    /** Un niveau de prompt : son nom d'affichage et la conversation envoyee au modele. */
    private record Niveau(String nom, List<Llm.Message> messages) {
    }

    /** Resultat du scoring d'une sortie : note sur 1 et explication lisible. */
    private record Score(double valeur, String detail) {
    }

    private Demo01MonteeEnGamme() {
    }

    // ------------------------------------------------------------------
    // Les quatre niveaux de prompt
    // ------------------------------------------------------------------

    private static List<Llm.Message> promptNaif() {
        return List.of(Llm.Message.utilisateur("Analyse cet avis : " + AVIS));
    }

    private static List<Llm.Message> promptStructure() {
        return List.of(
                Llm.Message.systeme("""
                        Tu es un analyste de la relation client. Tu extrais des informations \
                        structurees d'un avis. Contraintes : reponds UNIQUEMENT en JSON, en \
                        francais, sans texte autour. Champs attendus : sentiment \
                        (positif/negatif/mitige), produit, probleme, point_positif."""),
                Llm.Message.utilisateur("Avis : " + AVIS));
    }

    private static List<Llm.Message> promptFewShot() {
        String exempleEntree = "Super montre, mais le bracelet s'est casse en deux jours.";
        String exempleSortie = """
                {"sentiment": "mitige", "produit": "montre", \
                "probleme": "bracelet casse", "point_positif": "qualite de la montre"}""";
        // Le tour assistant preenregistre est le coeur du few-shot : nous
        // montrons au modele une reponse a imiter, plutot que de la decrire.
        return List.of(
                Llm.Message.systeme("""
                        Tu es un analyste de la relation client. Reponds UNIQUEMENT en JSON, \
                        en francais. Champs : sentiment, produit, probleme, point_positif."""),
                Llm.Message.utilisateur("Avis : " + exempleEntree),
                Llm.Message.assistant(exempleSortie),
                Llm.Message.utilisateur("Avis : " + AVIS));
    }

    private static List<Llm.Message> promptGabarit() {
        // Gabarit d'equipe : schema explicite, valeurs autorisees, regle de
        // secours. C'est cette version que l'on versionne dans un depot, car
        // elle est la seule a etre reproductible et relisible par un tiers.
        String schema = """
                {
                  "sentiment": "positif|negatif|mitige",
                  "produit": "<nom du produit ou inconnu>",
                  "probleme": "<probleme principal ou aucun>",
                  "point_positif": "<point positif ou aucun>"
                }""";
        String exempleSortie = """
                {"sentiment": "mitige", "produit": "montre", \
                "probleme": "bracelet casse", "point_positif": "qualite"}""";
        return List.of(
                Llm.Message.systeme("""
                        ROLE : analyste de la relation client.
                        TACHE : extraire les informations cles d'un avis client.
                        CONTRAINTES : sortie STRICTEMENT au format JSON ci-dessous, en \
                        francais, aucune cle supplementaire, aucun texte hors du JSON. Si un \
                        champ est absent de l'avis, mettre 'aucun' ou 'inconnu'.
                        SCHEMA :
                        """ + schema),
                Llm.Message.utilisateur("Avis : Super montre, bracelet casse en 2 jours."),
                Llm.Message.assistant(exempleSortie),
                Llm.Message.utilisateur("Avis : " + AVIS));
    }

    // ------------------------------------------------------------------
    // Mesure
    // ------------------------------------------------------------------

    /**
     * Recupere le premier objet JSON exploitable d'une sortie textuelle.
     *
     * <p>Pourquoi ne pas simplement appeler {@code readTree} sur la sortie
     * entiere ? Parce qu'un modele bavard encadre volontiers son JSON de prose
     * ou de balises Markdown. Nous cherchons donc d'abord un objet court
     * (recherche non gourmande), puis le plus long possible : cela couvre a la
     * fois le JSON plat et le JSON imbrique.</p>
     */
    private static JsonNode extraireJson(String texte) {
        for (Pattern motif : List.of(
                Pattern.compile("\\{.*?\\}", Pattern.DOTALL),
                Pattern.compile("\\{.*\\}", Pattern.DOTALL))) {
            Matcher m = motif.matcher(texte);
            while (m.find()) {
                try {
                    JsonNode noeud = MAPPER.readTree(m.group());
                    if (noeud.isObject()) {
                        return noeud;
                    }
                } catch (Exception ignore) {
                    // Fragment non analysable : nous passons au candidat suivant.
                }
            }
        }
        return null;
    }

    private static Score scorer(String sortie) {
        JsonNode objet = extraireJson(sortie);
        if (objet == null) {
            return new Score(0.0, "JSON invalide ou absent");
        }
        long presents = CHAMPS_ATTENDUS.stream()
                .filter(c -> objet.hasNonNull(c) && !objet.get(c).asText().isBlank())
                .count();
        return new Score((double) presents / CHAMPS_ATTENDUS.size(),
                presents + "/" + CHAMPS_ATTENDUS.size() + " champs presents");
    }

    private static String extrait(String texte, int longueur) {
        String propre = texte.strip().replaceAll("\\s+", " ");
        return propre.length() <= longueur ? propre : propre.substring(0, longueur);
    }

    public static void main(String[] args) {
        System.out.println("=== Demo 1 : montee en gamme d'un prompt ===\n");
        System.out.println("Tache identique pour tous : extraire un JSON de l'avis :");
        System.out.println("  " + AVIS + "\n");

        List<Niveau> niveaux = List.of(
                new Niveau("1. NAIF", promptNaif()),
                new Niveau("2. STRUCTURE", promptStructure()),
                new Niveau("3. FEW-SHOT", promptFewShot()),
                new Niveau("4. GABARIT", promptGabarit()));

        for (Niveau niveau : niveaux) {
            // temperature 0 : nous voulons une demo rejouable a l'identique,
            // pas une demonstration de creativite.
            String sortie = Llm.chat(niveau.messages(), 0.0);
            Score score = scorer(sortie);
            System.out.println("--- " + niveau.nom() + " ---");
            System.out.println("  Sortie (extrait) : " + extrait(sortie, 160));
            // Locale.ROOT : sans cela, une JVM en locale francaise afficherait
            // "0,00" et la sortie ne correspondrait plus a la fiche de demo.
            System.out.printf(Locale.ROOT, "  Score = %.2f  (%s)%n%n",
                    score.valeur(), score.detail());
        }

        System.out.println("A souligner : la tache et le modele ne changent pas. Seul le PROMPT");
        System.out.println("change, et le score de structuration progresse. Un gabarit d'equipe");
        System.out.println("versionne rend ce resultat reproductible et partageable.");
    }
}
