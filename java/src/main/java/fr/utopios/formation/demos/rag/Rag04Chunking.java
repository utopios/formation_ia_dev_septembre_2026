package fr.utopios.formation.demos.rag;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import fr.utopios.formation.commun.Llm;
import fr.utopios.formation.commun.Recherche;
import fr.utopios.formation.commun.Recherche.Chunk;
import fr.utopios.formation.commun.Recherche.Resultat;
import fr.utopios.formation.demos.Donnees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * RAG 04 (Module 5) : dix methodes de chunking, sur trois documents reels,
 * mesurees — avec leur cout.
 *
 * <p>Les documents ne sont pas ecrits pour la demo : un docx de la direction
 * commerciale converti par markitdown (tableaux compris), une page
 * d'architecture avec schema ASCII et tableaux, et une classe Java du projet.
 * Sur chacun, deux questions dont on connait la reponse.</p>
 *
 * <p>Trois familles, plus les methodes qui regardent le sens :</p>
 * <ul>
 *   <li><b>Mecaniques</b>, sans regarder le contenu : fixe 500 ; fixe 500 avec
 *       100 de chevauchement ; fenetre glissante (500, chevauchement 350 —
 *       la redondance assumee).</li>
 *   <li><b>Structurelles</b>, sur les frontieres du texte : phrases et
 *       paragraphes regroupes jusqu'a la taille voulue (LangChain4j) ;
 *       recursif ({@code DocumentSplitters.recursive}, le compromis par
 *       defaut) ; structure du document (titres Markdown, methodes Java).</li>
 *   <li><b>Semantiques</b>, sur le sens : semantique (on vectorise chaque
 *       phrase et on coupe la ou la similarite entre voisines chute) ; par LLM
 *       (le modele designe les unites d'idees) ; propositions (le modele
 *       extrait des affirmations atomiques autonomes — sur le docx seulement,
 *       pour le cout).</li>
 * </ul>
 *
 * <p>Pour chaque methode : nombre et taille des chunks, si le passage en tete
 * contient la reponse, la similarite et la MARGE sur le deuxieme, et le cout
 * (vecteurs calcules, appels LLM, secondes). Puis une generation sur le
 * passage de tete de deux methodes.</p>
 *
 * <p>Lancement :
 * {@code OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.rag.Rag04Chunking"}</p>
 */
public class Rag04Chunking {

    private record Question(String texte, String reponseAttendue) {
    }

    private record Doc(String nom, String fichier, boolean code, List<Question> questions) {
    }

    /** Une methode : son nom, sa famille, si elle s'applique au code, et le decoupage. */
    private record Methode(String nom, String famille, boolean surCode, Function<Doc, List<Chunk>> decoupe) {
    }

    private static final List<Doc> DOCS = List.of(
            new Doc("spec (docx converti)", "rag/chunking/specification-remises.md", false, List.of(
                    new Question("Quel bonus de remise recoit un client Grand compte ?", "4 points"),
                    new Question("Pendant combien de temps la remise de premiere commande est-elle valable ?", "30 jours"))),
            new Doc("architecture (md)", "rag/chunking/architecture-commandes.md", false, List.of(
                    new Question("Quel est le delai d'expiration de l'appel a l'API Sage ?", "5 secondes"),
                    new Question("Quel separateur utilise le fichier d'export comptable ?", "point-virgule"))),
            new Doc("DiscountPolicy (java)", "rag/chunking/DiscountPolicy.java", true, List.of(
                    new Question("Quel taux de remise s'applique au palier de 20000 ?", "BigDecimal(\"9\")"),
                    new Question("Quelle exception est levee quand le taux depasse le plafond ?", "DiscountCapExceededException"))));

    /** Compteurs de cout, remis a zero avant chaque methode. */
    private static int appelsLlm;
    private static int vecteursIndexation;

    private static final List<Methode> METHODES = List.of(
            new Methode("fixe 500", "mecanique", true, d -> fixe(d, 500, 0)),
            new Methode("fixe 500 / 100", "mecanique", true, d -> fixe(d, 500, 100)),
            new Methode("glissante 500 / 350", "mecanique", true, d -> fixe(d, 500, 350)),
            new Methode("phrases (lc4j)", "structurelle", true, d -> langchain(d, new DocumentBySentenceSplitter(300, 0))),
            new Methode("paragraphes (lc4j)", "structurelle", true, d -> langchain(d, new DocumentByParagraphSplitter(500, 0))),
            new Methode("recursif (lc4j)", "structurelle", true, d -> langchain(d, DocumentSplitters.recursive(500, 50))),
            new Methode("structure du doc", "structurelle", true, Rag04Chunking::structurel),
            new Methode("semantique", "semantique", true, d -> semantique(d, 800)),
            new Methode("LLM (agentique)", "semantique", true, Rag04Chunking::parLlm),
            new Methode("propositions (LLM)", "semantique", false, Rag04Chunking::propositions));

    public static void main(String[] args) {
        System.out.println("=== RAG 04 : dix methodes de chunking sur trois documents reels, avec leur cout ===");
        System.out.println("    Tailles en caracteres (un token ≈ 4 caracteres en francais). LLM : " + Llm.modeleChat());
        System.out.println();

        for (Doc doc : DOCS) {
            String contenu = Donnees.lire(doc.fichier());
            System.out.println("##################################################################");
            System.out.printf("DOCUMENT : %s — %d caracteres%n", doc.nom(), contenu.length());
            for (int i = 0; i < doc.questions().size(); i++) {
                System.out.printf("  Q%d : %s   (reponse attendue : « %s »)%n", i + 1,
                        doc.questions().get(i).texte(), doc.questions().get(i).reponseAttendue());
            }
            System.out.println("##################################################################");
            System.out.printf("%-21s %-12s %6s %5s %5s %5s   %-20s %-20s %s%n",
                    "methode", "famille", "chunks", "moy", "min", "max", "Q1 trouve sim marge", "Q2 trouve sim marge", "cout (vect/LLM/s)");

            List<Chunk> fixes = null;
            for (Methode m : METHODES) {
                if (doc.code() && !m.surCode()) {
                    System.out.printf("%-21s %-12s %6s%n", m.nom(), m.famille(), "— (non joue sur ce document)");
                    continue;
                }
                if (!m.surCode() && doc != DOCS.get(0)) {
                    System.out.printf("%-21s %-12s %6s%n", m.nom(), m.famille(), "— (docx seulement, pour le cout)");
                    continue;
                }
                appelsLlm = 0;
                vecteursIndexation = 0;
                long t0 = System.currentTimeMillis();
                List<Chunk> chunks = m.decoupe().apply(doc);
                if (fixes == null) {
                    fixes = chunks;
                }
                List<double[]> vecteurs = Llm.embedDocuments(chunks.stream().map(Chunk::texte).toList());
                vecteursIndexation += chunks.size();
                StringBuilder ligne = new StringBuilder(String.format("%-21s %-12s %6d %5d %5d %5d   ",
                        m.nom(), m.famille(), chunks.size(), moyenne(chunks), min(chunks), max(chunks)));
                for (Question q : doc.questions()) {
                    List<Resultat> classement = Recherche.classerCosinus(chunks, vecteurs, Llm.embedQuery(q.texte()));
                    Chunk tete = classement.get(0).chunk();
                    boolean trouve = tete.texte().contains(q.reponseAttendue());
                    ligne.append(String.format(Locale.ROOT, "%-6s %.3f %.3f  ", trouve ? "OUI" : "non",
                            classement.get(0).score(), Recherche.marge(classement)));
                }
                ligne.append(String.format(Locale.ROOT, " %d / %d / %.0f", vecteursIndexation, appelsLlm,
                        (System.currentTimeMillis() - t0) / 1000.0));
                System.out.println(ligne);
            }
            System.out.println();
            montrerCoupe(fixes);
            System.out.println();
        }

        // ---- La generation : ce qu'un chunk coupe fait dire au modele -----------
        System.out.println("##################################################################");
        System.out.println("GENERATION sur le passage de tete — spec (docx), Q1, deux methodes");
        System.out.println("##################################################################");
        Doc spec = DOCS.get(0);
        Question q = spec.questions().get(0);
        for (String nom : List.of("fixe 500", "propositions (LLM)")) {
            Methode m = METHODES.stream().filter(x -> x.nom().equals(nom)).findFirst().orElseThrow();
            List<Chunk> chunks = m.decoupe().apply(spec);
            List<double[]> vecteurs = Llm.embedDocuments(chunks.stream().map(Chunk::texte).toList());
            Chunk tete = Recherche.classerCosinus(chunks, vecteurs, Llm.embedQuery(q.texte())).get(0).chunk();
            String reponse = Llm.chat("CONTEXTE :\n" + tete.texte() + "\n\nQUESTION : " + q.texte(),
                    "Tu reponds uniquement a partir du CONTEXTE fourni, en une phrase. Si l'information n'y est pas, dis-le.",
                    0.0);
            System.out.printf("%n  [%s] passage de tete (%d car.) : %s%n", nom, tete.texte().length(),
                    Recherche.tronquer(tete.texte(), 110));
            System.out.printf("  -> %s%n", Recherche.tronquer(reponse, 220));
        }

        System.out.println();
        System.out.println("=== Ce qu'il faut en retenir ===");
        System.out.println("  Mecanique : la coupe tombe au milieu d'un tableau ou d'une methode ; le chevauchement repare une partie");
        System.out.println("  des coupes, la fenetre glissante presque toutes — au prix du stockage et de doublons dans le top k.");
        System.out.println("  Structurel : le document sait ou il se coupe (titres, methodes) et donne une CITATION lisible.");
        System.out.println("  Semantique et LLM : les chunks les plus homogenes, au prix de vecteurs ou d'appels a l'indexation —");
        System.out.println("  et un petit modele echoue parfois a segmenter : la trace le dit, la mesure le compte.");
        System.out.println("  Aucune methode ne gagne partout : la mesure sur VOS documents remplace l'opinion.");
    }

    // -----------------------------------------------------------------
    // Mecaniques
    // -----------------------------------------------------------------

    /** Coupe tous les {@code taille} caracteres, en reculant de {@code chevauchement} a chaque pas. */
    static List<Chunk> fixe(Doc doc, int taille, int chevauchement) {
        String texte = Donnees.lire(doc.fichier());
        List<Chunk> chunks = new ArrayList<>();
        int pas = taille - chevauchement;
        for (int debut = 0, n = 1; debut < texte.length(); debut += pas, n++) {
            int fin = Math.min(texte.length(), debut + taille);
            chunks.add(new Chunk(doc.nom(), "morceau " + n, texte.substring(debut, fin)));
            if (fin == texte.length()) {
                break;
            }
        }
        return chunks;
    }

    // -----------------------------------------------------------------
    // Structurelles
    // -----------------------------------------------------------------

    /** Un splitter LangChain4j : le meme code pour phrases, paragraphes et recursif. */
    static List<Chunk> langchain(Doc doc, DocumentSplitter splitter) {
        List<TextSegment> segments = splitter.split(Document.from(Donnees.lire(doc.fichier())));
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            chunks.add(new Chunk(doc.nom(), "segment " + (i + 1), segments.get(i).text()));
        }
        return chunks;
    }

    /** Markdown : une section de titre. Java : une methode (ou un bloc) avec sa javadoc. */
    static List<Chunk> structurel(Doc doc) {
        String texte = Donnees.lire(doc.fichier());
        if (!doc.code()) {
            return Recherche.sectionsMarkdown(doc.nom(), texte);
        }
        String[] blocs = texte.split("(?m)(?=^ {4}/\\*\\*)");
        List<Chunk> chunks = new ArrayList<>();
        for (String bloc : blocs) {
            String b = bloc.strip();
            if (b.isEmpty()) {
                continue;
            }
            String signature = b.lines()
                    .map(String::strip)
                    .filter(l -> l.startsWith("public ") || l.startsWith("private ") || l.startsWith("protected "))
                    .findFirst().orElse(b.lines().findFirst().orElse("?").strip());
            chunks.add(new Chunk(doc.nom(), Recherche.tronquer(signature, 50), b));
        }
        return chunks;
    }

    // -----------------------------------------------------------------
    // Semantiques
    // -----------------------------------------------------------------

    /**
     * Decoupage semantique : une unite par phrase (ou par ligne pour du code),
     * un vecteur par unite, et une coupe la ou la similarite entre deux
     * voisines tombe dans le quart le plus bas. Les unites sont ensuite
     * regroupees, sans depasser {@code maxCaracteres}.
     */
    static List<Chunk> semantique(Doc doc, int maxCaracteres) {
        String texte = Donnees.lire(doc.fichier());
        List<String> unites = new ArrayList<>();
        String[] brutes = doc.code() ? texte.split("\\n(?=\\s*\\n|\\s*/\\*\\*|\\s*(public|private|protected) )")
                : texte.split("(?<=[.!?])\\s+|\\n{2,}");
        for (String u : brutes) {
            if (!u.isBlank()) {
                unites.add(u.strip());
            }
        }
        List<double[]> vecteurs = Llm.embedDocuments(unites);
        vecteursIndexation += unites.size();

        // Similarite entre voisines, et seuil au premier quartile.
        double[] voisines = new double[unites.size() - 1];
        for (int i = 0; i < voisines.length; i++) {
            voisines[i] = Llm.cosine(vecteurs.get(i), vecteurs.get(i + 1));
        }
        double[] triees = voisines.clone();
        Arrays.sort(triees);
        double seuil = triees.length == 0 ? 0 : triees[triees.length / 4];

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder courant = new StringBuilder(unites.get(0));
        int n = 1;
        for (int i = 1; i < unites.size(); i++) {
            boolean bascule = voisines[i - 1] < seuil;
            boolean tropLong = courant.length() + unites.get(i).length() > maxCaracteres;
            if (bascule || tropLong) {
                chunks.add(new Chunk(doc.nom(), "unite " + n++, courant.toString()));
                courant = new StringBuilder(unites.get(i));
            } else {
                courant.append(doc.code() ? "\n" : " ").append(unites.get(i));
            }
        }
        chunks.add(new Chunk(doc.nom(), "unite " + n, courant.toString()));
        return chunks;
    }

    /**
     * Decoupage par LLM : le modele designe les unites d'idees par leurs
     * premiers mots ; on retrouve ces mots dans le texte et on coupe la. Ce
     * qu'il ne retrouve pas est ignore ; s'il ne retrouve rien, on garde le
     * document entier et on le dit — un petit modele echoue souvent ici.
     */
    static List<Chunk> parLlm(Doc doc) {
        String texte = Donnees.lire(doc.fichier());
        List<Integer> coupes = new ArrayList<>();
        try {
            appelsLlm++;
            JsonNode json = Llm.chatJson("DOCUMENT :\n\n" + texte + "\n\nDecoupe ce document en unites d'idees autonomes "
                            + "(entre 5 et 15). Pour chaque unite, donne un titre court et, EXACTEMENT tels qu'ils apparaissent, "
                            + "les six premiers mots de l'unite. Reponds en JSON : {\"unites\": [{\"titre\": \"...\", \"debut\": \"...\"}]}",
                    "Tu segmentes des documents techniques. Tu ne resumes pas, tu ne reformules pas les debuts : tu les recopies.");
            String plat = texte.replaceAll("\\s+", " ");
            for (JsonNode u : json.path("unites")) {
                String debut = u.path("debut").asText("").replaceAll("\\s+", " ").strip();
                if (debut.length() < 8) {
                    continue;
                }
                int pos = plat.indexOf(debut);
                if (pos < 0) {
                    pos = plat.toLowerCase(Locale.ROOT).indexOf(debut.toLowerCase(Locale.ROOT));
                }
                if (pos > 0) {
                    coupes.add(pos);
                }
            }
        } catch (RuntimeException e) {
            // JSON inexploitable : aucune coupe.
        }
        // Les positions sont dans le texte aplati ; on decoupe ce texte-la (les retours a la ligne ne portent pas le sens).
        String plat = texte.replaceAll("\\s+", " ");
        coupes = coupes.stream().distinct().sorted().toList();
        List<Chunk> chunks = new ArrayList<>();
        int precedent = 0;
        int n = 1;
        for (int c : coupes) {
            if (c - precedent > 40) {
                chunks.add(new Chunk(doc.nom(), "unite " + n++, plat.substring(precedent, c).strip()));
                precedent = c;
            }
        }
        chunks.add(new Chunk(doc.nom(), "unite " + n, plat.substring(precedent).strip()));
        if (chunks.size() == 1) {
            System.out.println("    [LLM] aucune coupe retrouvee dans le texte : document garde entier (echec de segmentation)");
        }
        return chunks;
    }

    /**
     * Propositions : pour chaque section, le modele extrait des affirmations
     * atomiques, autonomes (sujet explicite, pas de pronom). Chaque
     * proposition devient un chunk qui cite sa section. Un appel LLM par
     * section : c'est la methode la plus chere, et la plus precise sur les
     * questions factuelles.
     */
    static List<Chunk> propositions(Doc doc) {
        List<Chunk> sections = Recherche.sectionsMarkdown(doc.nom(), Donnees.lire(doc.fichier()));
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk s : sections) {
            try {
                appelsLlm++;
                JsonNode json = Llm.chatJson("SECTION :\n\n" + s.texte() + "\n\nExtrais toutes les affirmations factuelles de cette "
                                + "section, une par ligne, chacune autonome et comprehensible seule (sujet explicite, valeurs "
                                + "et unites recopiees telles quelles, jamais de pronom). Reponds en JSON : {\"propositions\": [\"...\"]}",
                        "Tu extrais des propositions atomiques d'un document de reference. Tu n'inventes rien, tu recopies les valeurs.");
                for (JsonNode p : json.path("propositions")) {
                    if (p.isTextual() && p.asText().strip().length() > 15) {
                        chunks.add(new Chunk(doc.nom(), s.section(), p.asText().strip()));
                    }
                }
            } catch (RuntimeException e) {
                chunks.add(s); // section gardee telle quelle si le modele echoue
            }
        }
        return chunks;
    }

    // -----------------------------------------------------------------
    // Affichage
    // -----------------------------------------------------------------

    private static void montrerCoupe(List<Chunk> fixes) {
        if (fixes == null || fixes.size() < 2) {
            return;
        }
        int i = 0;
        for (int j = 0; j + 1 < fixes.size(); j++) {
            String fin = fixes.get(j).texte();
            String derniere = fin.substring(fin.lastIndexOf('\n') + 1);
            if (derniere.startsWith("|") || derniere.contains("BigDecimal")) {
                i = j;
                break;
            }
        }
        String avant = fixes.get(i).texte();
        String apres = fixes.get(i + 1).texte();
        System.out.printf("  Ou tombe la coupe « fixe 500 » entre les morceaux %d et %d :%n", i + 1, i + 2);
        System.out.println("      …" + Recherche.tronquer(avant.substring(Math.max(0, avant.length() - 90)), 90) + "  ‖  "
                + Recherche.tronquer(apres.substring(0, Math.min(90, apres.length())), 90) + "…");
    }

    private static int moyenne(List<Chunk> chunks) {
        return (int) chunks.stream().mapToInt(c -> c.texte().length()).average().orElse(0);
    }

    private static int min(List<Chunk> chunks) {
        return chunks.stream().mapToInt(c -> c.texte().length()).min().orElse(0);
    }

    private static int max(List<Chunk> chunks) {
        return chunks.stream().mapToInt(c -> c.texte().length()).max().orElse(0);
    }
}
