package fr.utopios.formation.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.generated.AssistantTurnStartEvent;
import com.github.copilot.generated.SessionUsageInfoEvent;
import com.github.copilot.generated.ToolExecutionCompleteEvent;
import com.github.copilot.generated.ToolExecutionStartEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.McpServerConfig;
import com.github.copilot.rpc.McpStdioServerConfig;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolResultObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DemoHarnessCopilot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Le projet sur lequel l'agent travaille : celui des ateliers Copilot. */
    private static final Path PROJET = Path.of("..", "atelier-copilot");

    /** La declaration des connecteurs, partagee avec VS Code. */
    private static final Path MCP_JSON = Path.of(".vscode", "mcp.json");

    /** Borne de tours : au-dela, le harness coupe. */
    private static final int MAX_TOURS = 8;

    /** Borne de temps sur l'ensemble de la demande. */
    private static final Duration LIMITE = Duration.ofMinutes(5);

    /**
     * Ce que chaque serveur MCP a le droit d'exposer a l'agent. Le serveur
     * Atlassian expose 98 outils, GitLab 65 : on n'en laisse passer que les
     * lectures dont la demande a besoin. C'est le {@code tools:} d'un
     * {@code .agent.md}, applique depuis le harness.
     */
    private static final Map<String, List<String>> OUTILS_AUTORISES = Map.of(
            "atlassian", List.of("jira_get_issue", "jira_search",
                    "confluence_get_page", "confluence_search"),
            "gitlab", List.of("get_merge_request", "get_merge_request_diffs",
                    "list_merge_request_changed_files", "get_file_contents"));

    /**
     * La demande. Elle exige les trois origines d'outils pour etre traitee
     * honnetement : Jira et Confluence par MCP, le code par l'outil maison.
     * C'est ce qui rend la boucle observable sur toute sa largeur.
     */
    private static final String DEMANDE = """
            Dans Jira, trouve le ticket dont le titre commence par « Reprise des
            remises negociees ». Puis, dans Confluence, lis la page « Regles de
            remise » et dis ce qu'elle impose au sujet du fichier commercial
            que ce ticket mentionne. Enfin, verifie dans le code du projet que
            le plafond de remise cite par la page est bien present : utilise
            l'outil chercher_constantes_bigdecimal.
            Cite chaque source (ticket, page et section, fichier et ligne).
            Termine par une ligne : SOURCES=<nombre de sources citees>""";

    private static final Pattern MARQUEUR = Pattern.compile("\\$\\{([a-zA-Z]+)(?::([a-zA-Z_]+))?}");

    private DemoHarnessCopilot() {
    }

    // -----------------------------------------------------------------
    // Ce que le harness recolte
    // -----------------------------------------------------------------

    private record AppelOutil(int tour, String outil, String origine, String detail) {
    }

    private static final class Trace {
        int tours;
        final List<AppelOutil> appels = new ArrayList<>();
        final Map<String, Integer> parOutil = new LinkedHashMap<>();
        final Map<String, Integer> parOrigine = new LinkedHashMap<>();
        final List<String> serveursBranches = new ArrayList<>();
        final List<String> serveursIgnores = new ArrayList<>();
        int permissionsAccordees;
        int permissionsRefusees;
        int appelsOutilMaison;
        String modele = "?";
        long tokensContexte;
        long limiteContexte;
        String reponseFinale = "";
        boolean interrompu;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Demo 15 — piloter un agent Copilot par le SDK, sur Jira, Confluence et le code ===");
        System.out.println();

        Path projet = PROJET.toAbsolutePath().normalize();
        if (!Files.isDirectory(projet)) {
            System.err.println("Projet introuvable : " + projet);
            System.err.println("Lancer cette classe depuis le repertoire java/ du depot.");
            return;
        }
        String cli = cheminCli();
        if (cli == null) {
            expliquerCliManquant();
            return;
        }

        Trace trace = new Trace();
        Map<String, McpServerConfig> serveurs = serveursMcp(projet.resolve(MCP_JSON), projet, trace);

        System.out.println("Projet      : " + projet.getFileName());
        System.out.println("CLI         : " + cli);
        System.out.println("Connecteurs : " + (trace.serveursBranches.isEmpty() ? "aucun"
                : String.join(", ", trace.serveursBranches))
                + (trace.serveursIgnores.isEmpty() ? "" : "  (ignores : "
                + String.join(", ", trace.serveursIgnores) + ")"));
        System.out.println("Bornes      : " + MAX_TOURS + " tours, " + LIMITE.toMinutes() + " min");
        System.out.println();

        Instant debut = Instant.now();
        CopilotClientOptions options = new CopilotClientOptions()
                .setCliPath(cli)
                .setCwd(projet.toString());

        try (CopilotClient client = new CopilotClient(options)) {
            client.start().get();
            System.out.println("  [client]  CLI demarre, etat " + client.getState());

            SessionConfig config = new SessionConfig()
                    .setWorkingDirectory(projet.toString())
                    .setOnPermissionRequest(permissions(trace))
                    .setTools(List.of(outilMaison(trace, projet)))
                    .setStreaming(false);
            if (!serveurs.isEmpty()) {
                config.setMcpServers(serveurs);
            }
            String modele = System.getenv("COPILOT_MODEL");
            if (modele != null && !modele.isBlank()) {
                config.setModel(modele);
            }

            try (CopilotSession session = client.createSession(config).get()) {
                System.out.println("  [session] " + session.getSessionId());
                observer(session, trace);

                AssistantMessageEvent fin = session
                        .sendAndWait(new MessageOptions().setPrompt(DEMANDE), LIMITE.toMillis())
                        .get();
                if (fin != null && fin.getData() != null && fin.getData().content() != null) {
                    trace.reponseFinale = fin.getData().content().trim();
                }
            } catch (Exception e) {
                if (!trace.interrompu) {
                    throw e;
                }
            }
        }

        rendreCompte(trace, Duration.between(debut, Instant.now()));
    }

    // -----------------------------------------------------------------
    // 1. Les connecteurs : le pont entre .vscode/mcp.json et le SDK
    // -----------------------------------------------------------------

    /**
     * Lit la declaration VS Code des serveurs MCP et la traduit pour le SDK.
     *
     * <p>Deux choses que VS Code fait tout seul et qu'il faut refaire ici :
     * resoudre les {@code ${input:...}} (VS Code les demande a l'utilisateur
     * et les range dans le trousseau ; nous les lisons dans l'environnement ou
     * dans {@code ~/.netrc}, comme les scripts de simulation), et resoudre
     * {@code ${userHome}}.</p>
     *
     * <p>Un serveur dont un identifiant manque est <b>ignore</b>, pas lance a
     * moitie : un connecteur qui echoue a l'authentification produit des
     * erreurs que l'agent interprete parfois comme « il n'y a rien ».</p>
     */
    private static Map<String, McpServerConfig> serveursMcp(Path fichier, Path projet, Trace trace) {
        Map<String, McpServerConfig> serveurs = new LinkedHashMap<>();
        if (!Files.exists(fichier)) {
            System.out.println("  [mcp] " + fichier + " absent : aucun connecteur.");
            return serveurs;
        }
        JsonNode racine;
        try {
            racine = MAPPER.readTree(fichier.toFile());
        } catch (IOException e) {
            System.out.println("  [mcp] " + fichier + " illisible : " + e.getMessage());
            return serveurs;
        }

        racine.path("servers").fields().forEachRemaining(entree -> {
            String nom = entree.getKey();
            JsonNode s = entree.getValue();
            if (!s.has("command")) {
                trace.serveursIgnores.add(nom + " (pas de commande, transport non gere)");
                return;
            }

            Map<String, String> env = new LinkedHashMap<>();
            List<String> manquants = new ArrayList<>();
            s.path("env").fields().forEachRemaining(v -> {
                String valeur = resoudre(v.getValue().asText(), projet, manquants);
                env.put(v.getKey(), valeur);
            });
            if (!manquants.isEmpty()) {
                trace.serveursIgnores.add(nom + " (" + String.join(", ", manquants) + " non defini)");
                return;
            }

            List<String> args = new ArrayList<>();
            s.path("args").forEach(a -> args.add(a.asText()));

            McpStdioServerConfig cfg = new McpStdioServerConfig()
                    .setCommand(s.get("command").asText())
                    .setArgs(args)
                    .setEnv(env)
                    .setWorkingDirectory(projet.toString());

            // Le perimetre : on ne laisse passer que les outils de lecture
            // prevus pour ce serveur. Sans cette ligne, l'agent verrait les
            // 98 outils Atlassian, dont jira_delete_issue.
            List<String> autorises = OUTILS_AUTORISES.get(nom);
            if (autorises != null) {
                cfg.setTools(autorises);
            }
            serveurs.put(nom, cfg);
            trace.serveursBranches.add(nom + (autorises == null ? "" : " [" + autorises.size() + " outils]"));
        });
        return serveurs;
    }

    /** Remplace les marqueurs VS Code ; note ceux qu'on ne sait pas resoudre. */
    private static String resoudre(String valeur, Path projet, List<String> manquants) {
        Matcher m = MARQUEUR.matcher(valeur);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String genre = m.group(1);
            String nom = m.group(2);
            String remplacement = switch (genre) {
                case "userHome" -> System.getProperty("user.home");
                case "workspaceFolder" -> projet.toString();
                case "input" -> identifiant(nom);
                default -> null;
            };
            if (remplacement == null) {
                manquants.add(genre + (nom == null ? "" : ":" + nom));
                remplacement = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(remplacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Un {@code ${input:x}} se resout par la variable d'environnement X, puis
     * — pour les identifiants Atlassian — par {@code ~/.netrc}, comme le font
     * {@code remplir_jira.py} et {@code remplir_confluence.py}.
     */
    private static String identifiant(String nom) {
        String env = System.getenv(nom.toUpperCase(Locale.ROOT));
        if (env != null && !env.isBlank()) {
            return env;
        }
        if (nom.startsWith("atlassian_")) {
            String[] netrc = lireNetrc("atlassian.net");
            if (netrc != null) {
                return nom.endsWith("email") ? netrc[0] : netrc[1];
            }
        }
        return null;
    }

    /** Lit login et mot de passe de la premiere machine dont le nom contient {@code hote}. */
    private static String[] lireNetrc(String hote) {
        Path netrc = Path.of(System.getProperty("user.home"), ".netrc");
        if (!Files.isReadable(netrc)) {
            return null;
        }
        try {
            for (String ligne : Files.readAllLines(netrc)) {
                String[] mots = ligne.trim().split("\\s+");
                String login = null;
                String motDePasse = null;
                boolean bonneMachine = false;
                for (int i = 0; i + 1 < mots.length; i++) {
                    switch (mots[i]) {
                        case "machine" -> bonneMachine = mots[i + 1].contains(hote);
                        case "login" -> login = mots[i + 1];
                        case "password" -> motDePasse = mots[i + 1];
                        default -> { }
                    }
                }
                if (bonneMachine && login != null && motDePasse != null) {
                    return new String[] {login, motDePasse};
                }
            }
        } catch (IOException ignore) {
            // Un .netrc illisible equivaut a un .netrc absent.
        }
        return null;
    }

    // -----------------------------------------------------------------
    // 2. Les permissions : le harness voit et decide
    // -----------------------------------------------------------------

    /**
     * Tout ce qui LIT passe, tout ce qui ECRIT est refuse, chaque decision est
     * journalisee. C'est la difference avec {@code PermissionHandler.APPROVE_ALL}
     * — l'equivalent de {@code --allow-all-tools} — qui laisse faire sans que
     * personne ne voie. Un harness qui approuve tout est un tuyau.
     *
     * <p>Les outils d'ecriture MCP sont deja exclus en amont par
     * {@link #OUTILS_AUTORISES} : cette politique est la seconde barriere, et
     * elle couvre aussi les outils integres du CLI (edition de fichiers).</p>
     */
    private static PermissionHandler permissions(Trace trace) {
        return (demande, invocation) -> {
            String kind = String.valueOf(demande.getKind()).toLowerCase(Locale.ROOT);
            String detail = resumer(demande.getExtensionData());

            if (Boolean.TRUE.equals(demande.getManagedApprovalRequired())) {
                System.out.println("  [permission] " + kind + " -> politique geree, hors de notre main");
                return CompletableFuture.completedFuture(PermissionRequestResult.noResult());
            }
            boolean ecriture = kind.contains("write") || kind.contains("edit")
                    || kind.contains("create") || kind.contains("delete");
            if (ecriture) {
                trace.permissionsRefusees++;
                System.out.println("  [permission] " + kind + " -> REFUSEE (lecture seule) " + detail);
                return CompletableFuture.completedFuture(
                        PermissionRequestResult.reject("Ce harness n'autorise aucune ecriture."));
            }
            trace.permissionsAccordees++;
            System.out.println("  [permission] " + kind + " -> accordee " + detail);
            return CompletableFuture.completedFuture(PermissionRequestResult.approveOnce());
        };
    }

    // -----------------------------------------------------------------
    // 3. L'outil maison : du Java que l'agent peut appeler
    // -----------------------------------------------------------------

    private static ToolDefinition outilMaison(Trace trace, Path projet) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "valeur", Map.of(
                                "type", "string",
                                "description", "La valeur numerique cherchee, par exemple \"15\"")),
                "required", List.of("valeur"));

        return ToolDefinition.create(
                "chercher_constantes_bigdecimal",
                "Liste les classes Java du projet (hors tests) qui declarent new BigDecimal(\"<valeur>\"). "
                        + "Retourne fichier:ligne et la ligne de code. Deterministe et exhaustif : "
                        + "a preferer a un grep improvise.",
                schema,
                invocation -> {
                    trace.appelsOutilMaison++;
                    Object v = invocation.getArguments().get("valeur");
                    String valeur = v == null ? "" : v.toString().trim();
                    System.out.println("  [outil maison] chercher_constantes_bigdecimal(valeur=\"" + valeur + "\")");
                    try {
                        return CompletableFuture.completedFuture(
                                ToolResultObject.success(chercher(projet.resolve("src/main/java"), valeur)));
                    } catch (IOException e) {
                        return CompletableFuture.completedFuture(
                                ToolResultObject.error("Lecture du projet impossible : " + e.getMessage()));
                    }
                });
    }

    private static String chercher(Path racine, String valeur) throws IOException {
        String motif = "new BigDecimal(\"" + valeur + "\")";
        StringBuilder sb = new StringBuilder();
        int trouves = 0;
        try (Stream<Path> fichiers = Files.walk(racine)) {
            for (Path f : (Iterable<Path>) fichiers.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lignes = Files.readAllLines(f);
                for (int i = 0; i < lignes.size(); i++) {
                    if (lignes.get(i).contains(motif)) {
                        trouves++;
                        sb.append(racine.relativize(f)).append(':').append(i + 1)
                                .append("  ").append(lignes.get(i).trim()).append('\n');
                    }
                }
            }
        }
        return trouves == 0
                ? "Aucune occurrence de " + motif + " dans " + racine.getFileName()
                : trouves + " occurrence(s) :\n" + sb;
    }

    // -----------------------------------------------------------------
    // 4. L'observation et la borne
    // -----------------------------------------------------------------

    private static void observer(CopilotSession session, Trace trace) {
        session.on(AssistantTurnStartEvent.class, e -> {
            trace.tours++;
            if (e.getData().model() != null) {
                trace.modele = e.getData().model();
            }
            System.out.println();
            System.out.println("  --- TOUR " + trace.tours + " ---");
            if (trace.tours > MAX_TOURS) {
                trace.interrompu = true;
                System.out.println("  [harness] BORNE ATTEINTE (" + MAX_TOURS + " tours) : interruption.");
                session.abort();
            }
        });

        session.on(ToolExecutionStartEvent.class, e -> {
            var d = e.getData();
            String origine;
            if ("chercher_constantes_bigdecimal".equals(d.toolName())) {
                origine = "maison";
            } else if (d.mcpServerName() != null) {
                origine = "mcp:" + d.mcpServerName();
            } else {
                origine = "integre";
            }
            String nomAffiche = d.mcpToolName() != null ? d.mcpToolName() : d.toolName();
            String detail = abreger(String.valueOf(d.arguments()), 90);
            System.out.println("  [outil]   " + nomAffiche + " (" + origine + ") " + detail);
            trace.appels.add(new AppelOutil(trace.tours, nomAffiche, origine, detail));
            trace.parOutil.merge(nomAffiche + " (" + origine + ")", 1, Integer::sum);
            trace.parOrigine.merge(origine, 1, Integer::sum);
        });

        session.on(ToolExecutionCompleteEvent.class, e -> {
            if (Boolean.FALSE.equals(e.getData().success())) {
                System.out.println("  [outil]   -> ECHEC "
                        + (e.getData().error() != null ? e.getData().error() : ""));
            }
        });

        session.on(AssistantMessageEvent.class, e -> {
            var d = e.getData();
            if (d.model() != null && !d.model().isBlank()) {
                trace.modele = d.model();
            }
            String contenu = d.content() == null ? "" : d.content().trim();
            int demandes = d.toolRequests() == null ? 0 : d.toolRequests().size();
            if (!contenu.isEmpty()) {
                System.out.println("  [modele]  " + abreger(contenu, 100));
            }
            if (demandes > 0) {
                System.out.println("  [modele]  demande " + demandes + " appel(s) d'outil");
            }
        });

        session.on(SessionUsageInfoEvent.class, e -> {
            var d = e.getData();
            if (d.currentTokens() != null) {
                trace.tokensContexte = d.currentTokens();
            }
            if (d.tokenLimit() != null) {
                trace.limiteContexte = d.tokenLimit();
            }
        });
    }

    // -----------------------------------------------------------------
    // Le compte rendu
    // -----------------------------------------------------------------

    private static void rendreCompte(Trace trace, Duration duree) {
        System.out.println();
        System.out.println("=== Ce que la boucle a reellement fait ===");
        System.out.println();
        System.out.printf("  Tours                  : %d%s%n", trace.tours,
                trace.interrompu ? "  (interrompu par la borne)" : "");
        System.out.printf("  Appels d'outils        : %d%n", trace.appels.size());
        trace.parOutil.forEach((k, n) -> System.out.printf("      %-44s %d fois%n", k, n));
        System.out.printf("  Par origine            : %s%n", trace.parOrigine);
        System.out.printf("  Permissions            : %d accordee(s), %d refusee(s)%n",
                trace.permissionsAccordees, trace.permissionsRefusees);
        System.out.printf("  Modele                 : %s%n", trace.modele);
        if (trace.limiteContexte > 0) {
            System.out.printf("  Contexte               : %d / %d tokens (%.0f %%)%n",
                    trace.tokensContexte, trace.limiteContexte,
                    100.0 * trace.tokensContexte / trace.limiteContexte);
        }
        System.out.printf("  Duree                  : %.1f s%n", duree.toMillis() / 1000.0);

        System.out.println();
        System.out.println("=== La reponse ===");
        System.out.println();
        System.out.println(trace.reponseFinale.isEmpty()
                ? "  (aucune reponse : l'agent n'a rien conclu)"
                : "  " + trace.reponseFinale.replace("\n", "\n  "));

        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println();
        if (trace.appels.isEmpty()) {
            System.out.println("  ATTENTION : aucun outil appele. L'agent a repondu de memoire alors");
            System.out.println("  que la demande exigeait trois verifications. La trace contredit une");
            System.out.println("  reponse convaincante — c'est exactement ce qu'elle sert a faire.");
        } else {
            long mcp = trace.parOrigine.keySet().stream().filter(k -> k.startsWith("mcp:")).count();
            System.out.println("  Trois origines d'outils possibles : integres au CLI, serveurs MCP");
            System.out.println("  de l'entreprise, code maison. Ici : " + trace.parOrigine.size()
                    + " origine(s) utilisee(s), dont " + mcp + " connecteur(s) MCP.");
            if (trace.appelsOutilMaison == 0) {
                System.out.println("  L'outil maison n'a pas ete appele : la verification du code a ete");
                System.out.println("  faite autrement, ou pas du tout. A lire dans la reponse.");
            }
        }
        System.out.println();
        System.out.println("  Ce qui est reste a nous : quels connecteurs, quels outils par");
        System.out.println("  connecteur, quelles actions autorisees, et la borne. Le reste, c'est");
        System.out.println("  l'agent — et le code de l'outil maison n'a jamais quitte cette JVM.");
    }

    // -----------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------

    private static String resumer(Map<String, Object> extension) {
        if (extension == null || extension.isEmpty()) {
            return "";
        }
        for (String cle : List.of("command", "path", "fullCommandText", "url", "toolName", "serverName")) {
            Object v = extension.get(cle);
            if (v != null) {
                return "— " + abreger(String.valueOf(v), 80);
            }
        }
        return "— " + abreger(String.valueOf(extension.keySet()), 80);
    }

    private static String abreger(String texte, int max) {
        String plat = texte.replace('\n', ' ').trim();
        return plat.length() <= max ? plat : plat.substring(0, max - 1) + "…";
    }

    private static String cheminCli() {
        String force = System.getenv("COPILOT_CLI");
        if (force != null && !force.isBlank() && Files.isExecutable(Path.of(force))) {
            return force;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dossier : path.split(":")) {
                Path candidat = Path.of(dossier, "copilot");
                if (Files.isExecutable(candidat)) {
                    return candidat.toString();
                }
            }
        }
        return null;
    }

    private static void expliquerCliManquant() {
        System.err.println("Le CLI 'copilot' est introuvable.");
        System.err.println();
        System.err.println("Le SDK Java pilote le Copilot CLI mais ne l'embarque pas, contrairement");
        System.err.println("aux SDK Node et Python. Il faut donc l'installer et s'y connecter :");
        System.err.println();
        System.err.println("  npm install -g @github/copilot");
        System.err.println("  copilot          # puis /login");
        System.err.println();
        System.err.println("Ou, s'il est installe ailleurs que dans le PATH :");
        System.err.println("  export COPILOT_CLI=/chemin/vers/copilot");
    }
}
