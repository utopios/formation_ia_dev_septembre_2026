package fr.utopios.formation.tp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.utopios.formation.commun.Llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * TP 2 - Mini-harness agentique avec outils cadres et traces.
 *
 * <p>Ce que nous demontons ici : un agent, ce n'est pas un modele plus malin,
 * c'est une BOUCLE que nous ecrivons. Le modele ne fait qu'une chose : renvoyer
 * du texte. C'est notre code qui decide que ce texte est un appel d'outil, qui
 * execute l'outil, qui reinjecte le resultat et qui recommence. Le modele ne
 * touche jamais le disque : c'est le harness qui le fait, sous nos regles.</p>
 *
 * <p>Deux consequences que vous allez voir a l'ecran :</p>
 * <ul>
 *   <li>le MOINDRE PRIVILEGE est cote harness. Les outils refusent toute sortie
 *       du bac a sable, quoi que demande le modele ;</li>
 *   <li>la ROBUSTESSE est cote harness. llama3.2:1b n'a pas de tool calling
 *       natif fiable : il produit regulierement du JSON approximatif, ou un
 *       outil qui n'existe pas. La boucle doit survivre a cela, pas l'esperer
 *       parfait.</li>
 * </ul>
 */
public final class Tp02Solution {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Nombre maximal d'echanges avec le modele avant abandon. */
    private static final int MAX_TOURS = 6;

    // ======================================================================
    // Les outils : c'est ici que se joue la securite
    // ======================================================================

    /**
     * Un outil que l'agent peut appeler.
     *
     * @param nom         nom expose au modele
     * @param description signature rappelee dans le prompt systeme
     * @param execution   implementation, qui recoit les arguments JSON
     */
    public record Outil(String nom, String description, Function<JsonNode, String> execution) {
    }

    /** Bac a sable : aucune lecture n'est permise en dehors de cette racine. */
    private final Path racine;

    /** Journal des appels d'outils : c'est notre observabilite minimale. */
    private final List<String> journal = new ArrayList<>();

    private final Map<String, Outil> outils = new LinkedHashMap<>();

    public Tp02Solution(Path racine) {
        this.racine = racine.toAbsolutePath().normalize();
        enregistrer(new Outil("lister_fichiers",
                "lister_fichiers(sous_dossier) : liste les fichiers du projet",
                this::listerFichiers));
        enregistrer(new Outil("lire_fichier",
                "lire_fichier(chemin) : renvoie le contenu d'un fichier texte",
                this::lireFichier));
    }

    private void enregistrer(Outil outil) {
        outils.put(outil.nom(), outil);
    }

    /**
     * Resout un chemin et garantit qu'il reste sous la racine autorisee.
     *
     * <p>Le point critique est l'ordre des operations : nous normalisons
     * D'ABORD (ce qui aplatit les ".."), puis nous verifions. Verifier avant de
     * normaliser laisserait passer "src/../../../etc/passwd". C'est la meme
     * faille que la traversee de repertoire d'une application web classique ;
     * un agent ne change rien au probleme, il l'expose simplement a un
     * generateur de texte non deterministe.</p>
     */
    private Path resoudreDansBacASable(String chemin) {
        Path cible = racine.resolve(chemin).normalize();
        if (!cible.startsWith(racine)) {
            throw new SecurityException("acces refuse hors du bac a sable : " + chemin);
        }
        return cible;
    }

    private String listerFichiers(JsonNode args) {
        String sousDossier = args.path("sous_dossier").asText(".");
        if (sousDossier.isBlank()) {
            sousDossier = ".";
        }
        Path base;
        try {
            base = resoudreDansBacASable(sousDossier);
        } catch (SecurityException e) {
            return "ERREUR : " + e.getMessage();
        }
        if (!Files.isDirectory(base)) {
            return "ERREUR : dossier introuvable : " + sousDossier;
        }
        try (Stream<Path> flux = Files.walk(base)) {
            List<String> fichiers = flux
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/target/")
                            && !p.toString().contains("/.git/"))
                    .map(p -> racine.relativize(p).toString())
                    .sorted(Comparator.naturalOrder())
                    // On borne : le contexte du petit modele est une ressource rare.
                    .limit(40)
                    .toList();
            return fichiers.isEmpty() ? "(aucun fichier)" : String.join("\n", fichiers);
        } catch (IOException e) {
            return "ERREUR : parcours impossible (" + e.getMessage() + ")";
        }
    }

    private String lireFichier(JsonNode args) {
        String chemin = args.path("chemin").asText("");
        if (chemin.isBlank()) {
            return "ERREUR : argument 'chemin' absent.";
        }
        Path cible;
        try {
            cible = resoudreDansBacASable(chemin);
        } catch (SecurityException e) {
            return "ERREUR : " + e.getMessage();
        }
        if (!Files.isRegularFile(cible)) {
            return "ERREUR : fichier introuvable : " + chemin;
        }
        try {
            String contenu = Files.readString(cible);
            // Troncature volontaire : un fichier de 3000 lignes reinjecte dans
            // la conversation sature le contexte et fait perdre le fil a l'agent.
            return contenu.length() > 2500 ? contenu.substring(0, 2500) + "\n[...tronque]" : contenu;
        } catch (IOException e) {
            return "ERREUR : lecture impossible (" + e.getMessage() + ")";
        }
    }

    // ======================================================================
    // Le protocole : nous l'imposons, faute de tool calling natif fiable
    // ======================================================================

    private String promptSysteme() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un agent d'analyse de projet Java. Tu n'as AUCUNE connaissance ")
          .append("du projet : pour l'observer, tu DOIS utiliser les outils, et tu ne dois ")
          .append("rien inventer sur le contenu des fichiers.\n\n")
          .append("Outils disponibles :\n");
        for (Outil o : outils.values()) {
            sb.append("- ").append(o.description()).append('\n');
        }
        sb.append("""

                Tu reponds TOUJOURS par un unique objet JSON, sans aucun texte autour.

                Pour appeler un outil :
                {"outil": "lister_fichiers", "arguments": {"sous_dossier": "."}}

                Quand tu as assez d'informations pour repondre :
                {"reponse": "ton texte de reponse finale"}
                """);
        return sb.toString();
    }

    /**
     * Extrait le premier objet JSON d'une sortie de modele.
     *
     * <p>Fonction defensive assumee : le petit modele encadre souvent son JSON
     * d'un bloc markdown, ou ajoute une phrase de politesse avant. Plutot que
     * d'exiger la perfection, nous allons chercher le premier '{' et le dernier
     * '}'. C'est moins elegant qu'un parseur strict, mais c'est ce qui fait la
     * difference entre un agent qui tourne et un agent qui plante au tour 1.</p>
     *
     * @return l'objet JSON, ou null si rien d'exploitable
     */
    static JsonNode extraireJson(String texte) {
        int debut = texte.indexOf('{');
        int fin = texte.lastIndexOf('}');
        if (debut < 0 || fin <= debut) {
            return null;
        }
        try {
            JsonNode noeud = MAPPER.readTree(texte.substring(debut, fin + 1));
            return noeud.isObject() ? noeud : null;
        } catch (IOException e) {
            return null;
        }
    }

    // ======================================================================
    // La boucle du harness
    // ======================================================================

    /** Ce que la boucle a produit, avec de quoi la verifier apres coup. */
    public record Execution(String reponse, int tours, List<String> journal, boolean aborte) {
    }

    /**
     * Fait tourner l'agent jusqu'a la reponse finale ou l'epuisement des tours.
     *
     * <p>Remarquez la structure : a chaque tour nous rejouons TOUT l'historique.
     * Le modele n'a aucune memoire entre deux appels, c'est le harness qui la
     * porte. C'est aussi pour cela que le cout d'une conversation longue croit
     * plus vite qu'on ne l'imagine.</p>
     */
    public Execution executer(String demande) {
        List<Llm.Message> messages = new ArrayList<>();
        messages.add(Llm.Message.systeme(promptSysteme()));
        messages.add(Llm.Message.utilisateur(demande));

        // Memoire des appels deja servis, pour detecter les boucles steriles.
        java.util.Set<String> appelsDejaServis = new java.util.HashSet<>();

        for (int tour = 1; tour <= MAX_TOURS; tour++) {
            // Temperature 0 : un agent doit etre rejouable, pas inspire.
            final int numeroTour = tour;
            String sortie = Reessai.avecReessai("harness tour " + numeroTour,
                    () -> Llm.chat(messages, 0.0));
            System.out.println("\n--- Tour " + tour + " : sortie brute du modele ---");
            System.out.println(abreger(sortie, 300));

            JsonNode action = extraireJson(sortie);

            if (action == null) {
                // Robustesse 1 : JSON illisible. On ne plante pas, on recadre.
                journal.add("tour " + tour + " : JSON illisible, recadrage");
                messages.add(Llm.Message.assistant(sortie));
                messages.add(Llm.Message.utilisateur(
                        "Ta reponse n'etait pas un objet JSON. Reponds UNIQUEMENT par "
                                + "{\"outil\": ..., \"arguments\": {...}} ou {\"reponse\": \"...\"}."));
                continue;
            }

            // Robustesse 4 : le petit modele emet regulierement un objet qui
            // contient A LA FOIS "outil" et "reponse" -- autrement dit il annonce
            // un appel d'outil et invente deja son resultat dans la foulee. Si
            // nous lisions "reponse" en premier, nous accepterions une reponse
            // fabriquee sans qu'aucun fichier n'ait ete lu. L'appel d'outil est
            // donc PRIORITAIRE : tant qu'il y en a un, l'agent doit observer
            // avant de conclure.
            String nom = action.path("outil").asText("");
            JsonNode arguments = action.path("arguments");

            if (nom.isEmpty() && action.hasNonNull("reponse")) {
                journal.add("tour " + tour + " : reponse finale");
                return new Execution(action.get("reponse").asText(), tour, journal, false);
            }

            if (nom.isEmpty()) {
                // Robustesse 6 : un objet qui ne respecte pas le protocole -- ni
                // "outil" ni "reponse", mais un contenu libre, par exemple
                // {"fichiers": [...], "description": "..."}. llama3.2:3b le fait
                // volontiers : il a compris la question, pas la convention.
                // Deux cas, et l'ordre compte :
                //   - l'agent n'a encore rien observe : ce contenu est invente,
                //     on recadre en donnant la forme EXACTE attendue ;
                //   - l'agent a deja appele un outil : le contenu est fonde sur
                //     une observation reelle, on l'accepte comme reponse finale
                //     et on le note dans le journal. Le protocole est une
                //     convention que nous imposons au modele ; ce qu'on ne
                //     transige pas, c'est l'observation avant la conclusion.
                if (!appelsDejaServis.isEmpty()) {
                    journal.add("tour " + tour + " : reponse hors protocole acceptee apres observation reelle");
                    return new Execution(sortie.strip(), tour, journal, false);
                }
                journal.add("tour " + tour + " : objet JSON sans outil ni reponse, recadrage");
                messages.add(Llm.Message.assistant(sortie));
                messages.add(Llm.Message.utilisateur(
                        "Ton objet JSON ne contient ni \"outil\" ni \"reponse\". Tu n'as encore rien observe : "
                                + "appelle un outil, en repondant EXACTEMENT sous la forme "
                                + "{\"outil\": \"lister_fichiers\", \"arguments\": {\"sous_dossier\": \".\"}}"));
                continue;
            }

            // Robustesse 5 : la BOUCLE STERILE. llama3.2:1b redemande volontiers
            // le meme outil avec les memes arguments, tour apres tour, sans
            // jamais conclure. Un harness de production ne peut pas se contenter
            // d'une limite de tours : il doit detecter l'absence de progres.
            // Notre regle : si l'agent redemande un appel deja servi, nous
            // considerons qu'il a toutes les informations et nous exigeons une
            // conclusion. S'il avait deja redige une reponse anticipee, nous la
            // retenons -- elle est desormais fondee sur des resultats reels.
            String signature = nom + "|" + arguments;
            if (appelsDejaServis.contains(signature)) {
                journal.add("tour " + tour + " : appel repete '" + nom
                        + "', l'agent tourne en rond");
                if (action.hasNonNull("reponse")) {
                    journal.add("tour " + tour
                            + " : reponse anticipee retenue apres observation reelle");
                    return new Execution(action.get("reponse").asText(), tour, journal, false);
                }
                messages.add(Llm.Message.assistant(sortie));
                messages.add(Llm.Message.utilisateur(
                        "Tu as deja appele " + nom + " avec ces arguments et tu en as recu "
                                + "le resultat. Tu disposes de toutes les informations "
                                + "necessaires. Reponds MAINTENANT par {\"reponse\": \"...\"}, "
                                + "sans aucun appel d'outil."));
                continue;
            }
            appelsDejaServis.add(signature);

            if (action.hasNonNull("reponse")) {
                journal.add("tour " + tour
                        + " : reponse anticipee ignoree, appel d'outil prioritaire");
            }

            String resultat;
            if (!outils.containsKey(nom)) {
                // Robustesse 2 : outil inconnu. Le message d'erreur renvoye au
                // modele liste les outils reels : c'est ce qui lui permet de se
                // rattraper au tour suivant au lieu de s'obstiner.
                resultat = "ERREUR : outil inconnu '" + nom + "'. Outils disponibles : "
                        + String.join(", ", outils.keySet()) + ".";
                journal.add("tour " + tour + " : outil inconnu '" + nom + "'");
            } else {
                try {
                    resultat = outils.get(nom).execution().apply(arguments);
                    journal.add("tour " + tour + " : " + nom + "(" + arguments + ") -> "
                            + resultat.lines().count() + " ligne(s)");
                } catch (RuntimeException e) {
                    // Robustesse 3 : un outil qui leve ne doit pas tuer l'agent.
                    resultat = "ERREUR : " + e.getMessage();
                    journal.add("tour " + tour + " : " + nom + " a echoue");
                }
            }

            System.out.println("--- Outil " + nom + " -> " + abreger(resultat, 200));
            messages.add(Llm.Message.assistant(sortie));
            messages.add(Llm.Message.utilisateur(
                    "Resultat de l'outil " + nom + " :\n" + resultat));
        }
        journal.add("abandon : " + MAX_TOURS + " tours sans reponse finale");
        return new Execution("Nombre maximal de tours atteint sans reponse finale.",
                MAX_TOURS, journal, true);
    }

    private static String abreger(String texte, int max) {
        String propre = texte.strip();
        return propre.length() > max ? propre.substring(0, max) + " [...]" : propre;
    }

    // ======================================================================
    // Execution et verification
    // ======================================================================

    public static void main(String[] args) throws IOException {
        // Nous fabriquons un petit projet jetable : le TP doit etre rejouable a
        // l'identique, ce qu'un dossier reel ne garantit pas.
        Path racine = Files.createTempDirectory("tp02-projet");
        Files.writeString(racine.resolve("README.md"), """
                # Service de facturation ACME

                Ce module calcule les factures mensuelles des clients ACME.
                Il expose un unique point d'entree : FactureService.calculer(client, mois).
                Les taux de TVA sont lus dans le fichier taux.properties.
                """);
        Files.createDirectories(racine.resolve("src"));
        Files.writeString(racine.resolve("src/FactureService.java"), """
                public class FactureService {
                    public double calculer(String client, int mois) {
                        return 0.0;
                    }
                }
                """);
        Files.writeString(racine.resolve("taux.properties"), "tva.standard=0.20\n");
        System.out.println("=== TP 2 : mini-harness agentique ===");
        System.out.println("Bac a sable : " + racine);
        System.out.println("Modele : " + Llm.modeleChat());

        Tp02Solution agent = new Tp02Solution(racine);

        // ---- Verification 1 : le bac a sable tient, independamment du modele.
        // C'est une propriete de NOTRE code : elle se teste sans appeler le LLM.
        System.out.println("\n--- Verification du bac a sable (sans appel au modele) ---");
        String tentativeEvasion = agent.lireFichier(
                MAPPER.createObjectNode().put("chemin", "../../../../etc/passwd"));
        System.out.println("  lire_fichier(\"../../../../etc/passwd\") -> " + tentativeEvasion);
        if (!tentativeEvasion.startsWith("ERREUR : acces refuse")) {
            throw new AssertionError(
                    "Le bac a sable a laisse passer une traversee de repertoire : "
                            + tentativeEvasion);
        }

        // ---- Verification 2 : l'agent repond a partir de ce qu'il a LU.
        System.out.println("\n--- Execution de l'agent ---");
        Execution execution = agent.executer(
                "Quels fichiers composent ce projet, et que fait ce module d'apres son README ?");

        System.out.println("\n=== REPONSE FINALE (apres " + execution.tours() + " tour(s)) ===");
        System.out.println(execution.reponse());

        System.out.println("\n=== JOURNAL DES APPELS D'OUTILS ===");
        execution.journal().forEach(l -> System.out.println("  " + l));

        // ---- Verdict programmatique -----------------------------------------
        // Le critere n'est pas "la reponse est jolie" mais "l'agent a bien
        // utilise ses outils". Un agent qui repond sans avoir rien lu a
        // halluciné, meme si sa reponse tombe juste.
        long appelsOutils = execution.journal().stream()
                .filter(l -> l.contains("lister_fichiers") || l.contains("lire_fichier"))
                .count();
        System.out.println("\n=== VERIFICATION ===");
        System.out.println("Appels d'outils effectifs : " + appelsOutils);
        System.out.println("Traversee de repertoire bloquee : oui");

        if (appelsOutils == 0) {
            throw new AssertionError("L'agent n'a appele aucun outil : il a repondu "
                    + "de memoire, ce qui est exactement ce que le harness doit empecher.");
        }
        if (execution.aborte()) {
            throw new AssertionError("L'agent n'a pas converge en " + MAX_TOURS + " tours.");
        }
        System.out.println("L'agent a observe le projet par ses outils avant de repondre.");
    }
}
