package fr.utopios.formation.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public final class Demo03MiniHarness {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Repertoire bac a sable que l'agent est autorise a explorer.
     *
     * <p>Nous bornons l'agent a un projet jouet : c'est un moindre privilege
     * lisible en salle, et cela rend la demo reproductible quelle que soit la
     * machine.</p>
     */
    private static final Path BAC_A_SABLE = Donnees.chemin("demo-03/projet_demo");

    /** Un outil expose a l'agent : sa description pour le modele, et son code. */
    private record Outil(String description, Function<Map<String, String>, String> execution) {
    }

    /**
     * Une action decidee par le modele.
     *
     * <p>Soit un appel d'outil ({@code outil} renseigne), soit une conclusion
     * ({@code reponseFinale} renseignee). Modeliser les deux cas dans un seul
     * record reste plus simple a lire qu'une hierarchie scellee pour deux
     * variantes.</p>
     */
    private record Action(String outil, Map<String, String> parametres, String reponseFinale) {
    }

    private Demo03MiniHarness() {
    }

    // ------------------------------------------------------------------
    // Outils exposes a l'agent
    // ------------------------------------------------------------------

    /**
     * Empeche l'agent de sortir du bac a sable : moindre privilege.
     *
     * <p>Nous normalisons le chemin AVANT de comparer. Une comparaison sur la
     * chaine brute laisserait passer {@code ../../etc/passwd} : c'est la faille
     * classique de traversee de repertoire, et c'est exactement le genre de
     * garde qu'un harness doit porter dans son CODE, jamais dans son prompt.</p>
     */
    private static Path cheminSur(String relatif) {
        Path racine = BAC_A_SABLE.toAbsolutePath().normalize();
        Path cible = racine.resolve(relatif).normalize();
        if (!cible.startsWith(racine)) {
            throw new IllegalArgumentException("acces hors du bac a sable refuse");
        }
        return cible;
    }

    /** Liste les fichiers du projet (equivalent d'un 'ls'). */
    private static String outilListerFichiers(Map<String, String> params) {
        Path base = cheminSur(params.getOrDefault("sous_dossier", "."));
        try (Stream<Path> flux = Files.walk(base)) {
            String noms = flux.filter(Files::isRegularFile)
                    .map(p -> BAC_A_SABLE.toAbsolutePath().normalize().relativize(p).toString())
                    .sorted(Comparator.naturalOrder())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            return noms.isEmpty() ? "(aucun fichier)" : noms;
        } catch (IOException e) {
            return "erreur de lecture : " + e.getMessage();
        }
    }

    /** Lit le contenu d'un fichier du projet (equivalent d'un 'cat'). */
    private static String outilLireFichier(Map<String, String> params) {
        String chemin = params.get("chemin");
        if (chemin == null) {
            return "parametre 'chemin' manquant";
        }
        try {
            String contenu = Files.readString(cheminSur(chemin), StandardCharsets.UTF_8);
            // Nous tronquons : un fichier long noierait le contexte du petit
            // modele et ferait exploser le cout du tour suivant.
            return contenu.length() > 2000 ? contenu.substring(0, 2000) : contenu;
        } catch (IOException e) {
            return "fichier illisible : " + chemin;
        }
    }

    /** Cherche un motif dans les fichiers du projet (equivalent d'un 'grep -r'). */
    private static String outilChercher(Map<String, String> params) {
        String motif = params.get("motif");
        if (motif == null) {
            return "parametre 'motif' manquant";
        }
        Path racine = BAC_A_SABLE.toAbsolutePath().normalize();
        List<String> resultats = new ArrayList<>();
        try (Stream<Path> flux = Files.walk(racine)) {
            for (Path fichier : flux.filter(Files::isRegularFile).toList()) {
                List<String> lignes;
                try {
                    lignes = Files.readAllLines(fichier, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue; // Fichier binaire ou illisible : nous l'ignorons.
                }
                for (int i = 0; i < lignes.size() && resultats.size() < 30; i++) {
                    if (lignes.get(i).toLowerCase().contains(motif.toLowerCase())) {
                        resultats.add(racine.relativize(fichier) + ":" + (i + 1) + ": "
                                + lignes.get(i).strip());
                    }
                }
            }
        } catch (IOException e) {
            return "erreur de parcours : " + e.getMessage();
        }
        return resultats.isEmpty()
                ? "(motif '" + motif + "' introuvable)"
                : String.join("\n", resultats);
    }

    /** Commandes shell autorisees : liste blanche en LECTURE SEULE. */
    private static final List<String> COMMANDES_AUTORISEES =
            List.of("ls", "cat", "grep", "wc", "head", "tail", "find");

    /**
     * Execute une commande shell en lecture seule dans le bac a sable (demo 4).
     *
     * <p>Moindre privilege : liste blanche de commandes non destructives. En
     * salle, nous montrons que c'est le HARNESS qui decide ce que l'agent a le
     * droit de faire, pas le LLM. La securite est dans le code de l'outil, pas
     * dans la consigne systeme : un prompt se contourne, une liste blanche non.</p>
     */
    private static String outilBash(Map<String, String> params) {
        String commande = params.getOrDefault("commande", "").strip();
        String premier = commande.isEmpty() ? "" : commande.split("\\s+")[0];
        if (!COMMANDES_AUTORISEES.contains(premier)) {
            return "commande '" + premier + "' refusee (liste blanche : "
                    + COMMANDES_AUTORISEES + ")";
        }
        // Deuxieme garde, indispensable : la liste blanche autorise 'cat', et
        // 'cat ../../../etc/passwd' sortirait du bac a sable. Une liste blanche
        // de VERBES ne dit rien des OBJETS sur lesquels ils s'appliquent.
        // C'est l'erreur classique quand on outille un agent : on filtre la
        // commande et on oublie ses arguments.
        if (commande.contains("..") || commande.contains("~") || commande.matches(".*\\s/.*")) {
            return "arguments refuses : sortie du bac a sable (.. , ~ ou chemin absolu)";
        }
        try {
            Process processus = new ProcessBuilder("/bin/sh", "-c", commande)
                    .directory(BAC_A_SABLE.toFile())
                    .redirectErrorStream(true)
                    .start();
            String sortie = new String(processus.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            // Un timeout borne l'outil : sans lui, un 'find /' lance par erreur
            // bloquerait la demo entiere.
            if (!processus.waitFor(10, TimeUnit.SECONDS)) {
                processus.destroyForcibly();
                return "(timeout)";
            }
            if (sortie.length() > 2000) {
                sortie = sortie.substring(0, 2000);
            }
            return sortie.isBlank() ? "(pas de sortie)" : sortie;
        } catch (IOException e) {
            return "erreur d'execution : " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "execution interrompue";
        }
    }

    /** Catalogue des outils. LinkedHashMap : l'ordre d'affichage reste stable. */
    private static final Map<String, Outil> OUTILS = new LinkedHashMap<>(Map.of());

    static {
        OUTILS.put("lister_fichiers", new Outil(
                "Liste les fichiers du projet. Param: sous_dossier (str, optionnel).",
                Demo03MiniHarness::outilListerFichiers));
        OUTILS.put("lire_fichier", new Outil(
                "Lit un fichier. Param: chemin (str).",
                Demo03MiniHarness::outilLireFichier));
        OUTILS.put("chercher", new Outil(
                "Cherche un motif dans les fichiers. Param: motif (str).",
                Demo03MiniHarness::outilChercher));
        OUTILS.put("bash", new Outil(
                "Execute une commande shell lecture seule (ls, cat, grep, wc, head, tail, find). "
                        + "Param: commande (str).",
                Demo03MiniHarness::outilBash));
    }

    // ------------------------------------------------------------------
    // Protocole
    // ------------------------------------------------------------------

    private static String systemeAgent() {
        StringBuilder outils = new StringBuilder();
        OUTILS.forEach((nom, outil) ->
                outils.append("  - ").append(nom).append(" : ").append(outil.description()).append('\n'));
        return """
                Tu es un agent d'analyse de projet. Tu NE REDIGES JAMAIS de prose. Tu \
                reponds TOUJOURS et UNIQUEMENT par un seul objet JSON, rien avant, rien \
                apres.
                Pour utiliser un outil :
                  {"outil": "<nom>", "parametres": {...}}
                Pour repondre a l'utilisateur quand tu as fini :
                  {"reponse_finale": "<ta reponse>"}

                Outils disponibles :
                """
                + outils
                + """

                Exemples de reponses VALIDES (a imiter exactement) :
                  {"outil": "lister_fichiers", "parametres": {}}
                  {"outil": "lire_fichier", "parametres": {"chemin": "src/main/java/exemple/App.java"}}
                  {"outil": "chercher", "parametres": {"motif": "static "}}
                  {"outil": "bash", "parametres": {"commande": "wc -l src/main/java/exemple/App.java"}}
                  {"reponse_finale": "Le projet contient 2 fichiers Java."}

                Commence TOUJOURS par explorer avec lister_fichiers avant de conclure. \
                Ne devine jamais le contenu : lis les fichiers avec les outils.""";
    }

    /**
     * Recupere le premier objet JSON d'action dans la sortie du modele.
     *
     * <p>Le modele encadre souvent son JSON de prose ou de balises Markdown.
     * Nous essayons donc plusieurs candidats plutot que d'exiger une sortie
     * parfaite : c'est la tolerance qui rend le harness utilisable avec un
     * petit modele.</p>
     */
    private static Action extraireAction(String texte) {
        for (Pattern motif : List.of(
                Pattern.compile("\\{.*?\\}", Pattern.DOTALL),
                Pattern.compile("\\{.*\\}", Pattern.DOTALL))) {
            Matcher m = motif.matcher(texte);
            while (m.find()) {
                JsonNode noeud;
                try {
                    noeud = MAPPER.readTree(m.group());
                } catch (Exception ignore) {
                    continue;
                }
                if (!noeud.isObject()) {
                    continue;
                }
                if (noeud.has("reponse_finale")) {
                    return new Action(null, Map.of(), noeud.get("reponse_finale").asText());
                }
                if (noeud.has("outil")) {
                    Map<String, String> params = new LinkedHashMap<>();
                    JsonNode bruts = noeud.get("parametres");
                    if (bruts != null && bruts.isObject()) {
                        bruts.fields().forEachRemaining(e ->
                                params.put(e.getKey(), e.getValue().asText()));
                    }
                    return new Action(noeud.get("outil").asText(), params, null);
                }
            }
        }
        return null;
    }

    /** Affiche une etape de trace, lisible en salle : c'est le coeur de la demo 3. */
    private static void tracer(String etiquette, String contenu) {
        System.out.println("-".repeat(70));
        System.out.println("[" + etiquette + "]");
        System.out.println(contenu);
        System.out.println();
    }

    private static String tronquer(String texte, int longueur) {
        return texte.length() <= longueur ? texte : texte.substring(0, longueur);
    }

    // ------------------------------------------------------------------
    // Boucle du harness
    // ------------------------------------------------------------------

    private static String boucleAgent(String tache, int maxTours) {
        System.out.println("=".repeat(70));
        System.out.println("TACHE : " + tache);
        System.out.println("=".repeat(70) + "\n");

        List<Llm.Message> messages = new ArrayList<>();
        messages.add(Llm.Message.systeme(systemeAgent()));
        messages.add(Llm.Message.utilisateur(tache));
        tracer("TRACE messages initiaux",
                "system: (protocole + " + OUTILS.size() + " outils)\nuser: " + tache);

        for (int tour = 1; tour <= maxTours; tour++) {
            // Dernier tour : nous FORCONS la conclusion. Un petit modele ne
            // decide pas toujours de s'arreter ; le harness borne donc la
            // boucle et exige une reponse finale. Point pedagogique : c'est le
            // HARNESS qui controle la boucle, pas le modele.
            if (tour == maxTours) {
                messages.add(Llm.Message.utilisateur(
                        "Tu as assez explore. Donne MAINTENANT ta reponse a l'utilisateur "
                                + "au format {\"reponse_finale\": \"...\"}, sans appeler d'outil."));
            }

            // temperature 0 : nous voulons le comportement le plus deterministe
            // possible, le protocole JSON tolere mal la creativite du modele.
            String brut = Llm.chat(messages, 0.0);
            tracer("TOUR " + tour + " - reponse brute du LLM", brut.strip());

            Action action = extraireAction(brut);
            if (action == null) {
                tracer("TOUR " + tour + " - ACTION", "aucune action JSON valide -> on arrete");
                return brut.strip();
            }
            if (action.reponseFinale() != null) {
                tracer("TOUR " + tour + " - ACTION", "reponse finale de l'agent");
                return action.reponseFinale();
            }

            String nom = action.outil();
            tracer("TOUR " + tour + " - APPEL D'OUTIL", nom + "(" + action.parametres() + ")");

            Outil outil = OUTILS.get(nom);
            String resultat;
            if (outil == null) {
                resultat = "outil inconnu : " + nom;
            } else {
                try {
                    resultat = outil.execution().apply(action.parametres());
                } catch (RuntimeException e) {
                    // Nous renvoyons l'erreur AU MODELE plutot que de planter :
                    // un agent doit pouvoir se corriger a partir de son echec.
                    resultat = "erreur d'execution : " + e.getMessage();
                }
            }
            tracer("TOUR " + tour + " - RESULTAT D'OUTIL", tronquer(resultat, 600));

            // Nous reinjectons la trace dans la conversation : l'agent voit son
            // propre resultat. C'est ce qui distingue un agent d'un simple chat.
            messages.add(Llm.Message.assistant(brut));
            messages.add(Llm.Message.utilisateur(
                    "Resultat de l'outil " + nom + " :\n" + resultat + "\n\n"
                            + "Si tu as assez d'informations pour repondre a la tache, "
                            + "donne ta reponse_finale maintenant. Sinon, appelle un "
                            + "autre outil. Ne relance pas un outil deja utilise avec "
                            + "les memes parametres."));
        }
        return "(nombre maximal de tours atteint sans reponse finale)";
    }

    public static void main(String[] args) {
        String tache = args.length > 0 ? args[0]
                : "Analyse le projet : combien de fichiers Java contient-il, et que fait "
                  + "le fichier principal ? Utilise les outils a ta disposition.";
        String reponse = boucleAgent(tache, 6);
        System.out.println("=".repeat(70));
        System.out.println("REPONSE FINALE DE L'AGENT :");
        System.out.println(reponse);
        System.out.println("=".repeat(70));
    }
}
