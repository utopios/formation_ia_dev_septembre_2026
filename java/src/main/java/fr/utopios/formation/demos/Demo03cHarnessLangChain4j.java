package fr.utopios.formation.demos;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecution;
import fr.utopios.formation.commun.Llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


public final class Demo03cHarnessLangChain4j {

    /** Le bac a sable : l'agent ne lit rien en dehors. */
    private static final Path BAC_A_SABLE = Donnees.chemin("demo-03/projet_demo");

    private static final int MAX_APPELS_OUTILS = 8;

    private static final String TACHE = "Analyse le projet : combien de fichiers Java contient-il, et que fait "
            + "le fichier principal ? Utilise les outils a ta disposition, puis reponds en francais en trois phrases.";

    private Demo03cHarnessLangChain4j() {
    }

    // -----------------------------------------------------------------
    // 1. Les outils : du Java annote, un bac a sable, une politique
    // -----------------------------------------------------------------

    /**
     * Les memes outils que la Demo 3. LangChain4j lit ces signatures pour
     * decrire les outils au modele : le nom de la methode, la description de
     * {@code @Tool}, celle de chaque parametre ({@code @P}). Ce qu'il ne fait
     * pas : verifier que {@code chemin} reste dans le bac a sable, ou refuser
     * une commande. C'est notre code, et c'est la politique.
     */
    public static final class OutilsProjet {

        private static final Set<String> COMMANDES_AUTORISEES = Set.of("ls", "wc", "grep", "find");

        final List<String> journal = new ArrayList<>();

        @Tool("Liste les fichiers du projet, avec leur chemin relatif et leur taille en octets.")
        public String listerFichiers() {
            tracer("listerFichiers()");
            try (Stream<Path> fichiers = Files.walk(BAC_A_SABLE)) {
                StringBuilder sb = new StringBuilder();
                for (Path f : (Iterable<Path>) fichiers.filter(Files::isRegularFile).sorted()::iterator) {
                    sb.append(BAC_A_SABLE.relativize(f)).append("  (").append(Files.size(f)).append(" octets)\n");
                }
                return sb.toString();
            } catch (IOException e) {
                return "ERREUR : " + e.getMessage();
            }
        }

        @Tool("Lit le contenu complet d'un fichier du projet.")
        public String lireFichier(@P("chemin relatif du fichier, tel que rendu par listerFichiers") String chemin) {
            tracer("lireFichier(" + chemin + ")");
            Path p = cheminSur(chemin);
            if (p == null) {
                return "REFUSE : ce chemin sort du projet.";
            }
            try {
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "ERREUR : fichier introuvable : " + chemin;
            }
        }

        @Tool("Cherche un mot ou une expression dans tous les fichiers du projet ; rend fichier:ligne et la ligne.")
        public String chercher(@P("le texte exact a chercher") String motif) {
            tracer("chercher(" + motif + ")");
            StringBuilder sb = new StringBuilder();
            try (Stream<Path> fichiers = Files.walk(BAC_A_SABLE)) {
                for (Path f : (Iterable<Path>) fichiers.filter(Files::isRegularFile)::iterator) {
                    List<String> lignes = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (int i = 0; i < lignes.size(); i++) {
                        if (lignes.get(i).contains(motif)) {
                            sb.append(BAC_A_SABLE.relativize(f)).append(':').append(i + 1).append("  ").append(lignes.get(i).strip()).append('\n');
                        }
                    }
                }
            } catch (IOException e) {
                return "ERREUR : " + e.getMessage();
            }
            return sb.isEmpty() ? "Aucune occurrence de « " + motif + " »." : sb.toString();
        }

        @Tool("Execute une commande shell dans le projet. Seules ls, wc, grep et find sont autorisees.")
        public String executerCommande(@P("la commande, par exemple : wc -l src/main/java/exemple/App.java") String commande) {
            tracer("executerCommande(" + commande + ")");
            String programme = commande.strip().split("\\s+")[0];
            if (!COMMANDES_AUTORISEES.contains(programme)) {
                // Le refus est un RESULTAT d'outil : le modele le lit et doit s'adapter.
                return "REFUSE par la politique du harness : « " + programme + " » n'est pas dans la liste "
                        + COMMANDES_AUTORISEES + ". Utilise un autre outil.";
            }
            if (commande.contains("..") || commande.contains("/etc") || commande.contains("~")) {
                return "REFUSE : la commande sort du projet.";
            }
            try {
                Process p = new ProcessBuilder("sh", "-c", commande).directory(BAC_A_SABLE.toFile())
                        .redirectErrorStream(true).start();
                String sortie = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
                return sortie.isBlank() ? "(aucune sortie)" : sortie;
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERREUR : " + e.getMessage();
            }
        }

        private static Path cheminSur(String relatif) {
            Path p = BAC_A_SABLE.resolve(relatif).normalize();
            return p.startsWith(BAC_A_SABLE) ? p : null;
        }

        private void tracer(String appel) {
            journal.add(appel);
            System.out.println("  [outil]   " + appel);
        }
    }

    // -----------------------------------------------------------------
    // 2. L'agent : une interface, LangChain4j fait la boucle
    // -----------------------------------------------------------------

    /** Le contrat de l'agent. {@code Result<String>} donne, en plus de la reponse, chaque appel d'outil et les tokens. */
    interface Agent {
        @SystemMessage("""
                Tu es un assistant qui analyse un petit projet Java a l'aide d'outils.
                Tu appelles les outils pour verifier ; tu n'inventes jamais un contenu de fichier.
                Quand tu as ce qu'il faut, tu reponds en francais, brievement.""")
        Result<String> executer(@UserMessage String tache);
    }

    // -----------------------------------------------------------------
    // 3. L'observation : chaque aller-retour vers le modele
    // -----------------------------------------------------------------

    /** Un tour = une requete au modele. On compte, et on lit ce que le modele demande. */
    static final class Observateur implements ChatModelListener {
        int tours;
        int tokensEntree;
        int tokensSortie;

        @Override
        public void onRequest(ChatModelRequestContext ctx) {
            tours++;
            System.out.println();
            System.out.println("  --- TOUR " + tours + " : " + ctx.chatRequest().messages().size() + " message(s) envoye(s) au modele ---");
        }

        @Override
        public void onResponse(ChatModelResponseContext ctx) {
            var reponse = ctx.chatResponse();
            TokenUsage u = reponse.tokenUsage();
            if (u != null) {
                tokensEntree += u.inputTokenCount() == null ? 0 : u.inputTokenCount();
                tokensSortie += u.outputTokenCount() == null ? 0 : u.outputTokenCount();
            }
            var msg = reponse.aiMessage();
            if (msg.hasToolExecutionRequests()) {
                msg.toolExecutionRequests().forEach(r ->
                        System.out.println("  [modele]  demande l'outil " + r.name() + " " + r.arguments()));
            } else {
                System.out.println("  [modele]  repond sans outil : " + tronquer(msg.text(), 90));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Demo 3c : le harness de la Demo 3, ecrit avec LangChain4j ===");
        System.out.println("Modele : " + Llm.modeleChat() + "  |  bac a sable : " + BAC_A_SABLE.getFileName()
                + "  |  borne : " + MAX_APPELS_OUTILS + " appels d'outils");
        System.out.println("TACHE : " + TACHE);

        Observateur observateur = new Observateur();
        OutilsProjet outils = new OutilsProjet();

        OllamaChatModel modele = OllamaChatModel.builder()
                .baseUrl(Llm.BASE_URL)
                .modelName(Llm.modeleChat())
                .temperature(0.0)
                .timeout(Duration.ofMinutes(5))
                .listeners(List.of(observateur))
                .build();

        Agent agent = AiServices.builder(Agent.class)
                .chatModel(modele)
                .tools(outils)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(30))
                .maxSequentialToolsInvocations(MAX_APPELS_OUTILS)
                .build();

        Instant debut = Instant.now();
        Result<String> resultat;
        try {
            resultat = agent.executer(TACHE);
        } catch (RuntimeException e) {
            // La borne de LangChain4j se manifeste par une exception : c'est le harness qui coupe.
            System.out.println();
            System.out.println("  [harness] ARRET : " + e.getMessage());
            rendreCompte(observateur, outils, null, Duration.between(debut, Instant.now()));
            return;
        }
        rendreCompte(observateur, outils, resultat, Duration.between(debut, Instant.now()));
    }

    private static void rendreCompte(Observateur obs, OutilsProjet outils, Result<String> resultat, Duration duree) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("REPONSE FINALE DE L'AGENT :");
        System.out.println(resultat == null ? "(aucune : boucle interrompue)" : resultat.content());
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("=== Ce que la boucle a reellement fait ===");
        System.out.printf("  Tours (requetes au modele) : %d%n", obs.tours);
        System.out.printf("  Appels d'outils            : %d  %s%n", outils.journal.size(), outils.journal);
        if (resultat != null) {
            System.out.println("  Relecture par Result.toolExecutions() :");
            for (ToolExecution te : resultat.toolExecutions()) {
                System.out.printf("      %-18s %-40s -> %s%n", te.request().name(), tronquer(te.request().arguments(), 40),
                        tronquer(te.result(), 60));
            }
        }
        System.out.printf("  Tokens                     : %d en entree, %d en sortie (cumul des tours)%n", obs.tokensEntree, obs.tokensSortie);
        System.out.printf("  Duree                      : %.1f s%n", duree.toMillis() / 1000.0);
        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  LangChain4j a ecrit le schema des outils, analyse les appels, fait la boucle et tenu la memoire.");
        System.out.println("  Le bac a sable, la liste des commandes, la borne et la trace sont a nous — et c'est la");
        System.out.println("  meme repartition qu'avec le SDK Copilot : le framework fait la mecanique, le harness fait la politique.");
    }

    private static String tronquer(String s, int max) {
        String plat = s == null ? "" : s.replace('\n', ' ').replaceAll("\\s+", " ").strip();
        return plat.length() <= max ? plat : plat.substring(0, max - 1) + "…";
    }
}
