package fr.utopios.formation.demos;

import fr.utopios.formation.commun.Llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo 1 — variante "chaine de prompts" (Module 1, slide « Chaine de prompts
 * en Java »).
 *
 * <p>Objectif pedagogique : montrer qu'une tache composee se decoupe en
 * MAILLONS, chacun avec son propre prompt, testable isolement. La sortie d'un
 * maillon devient l'entree du suivant.</p>
 *
 * <p>Deux maillons ici :</p>
 * <ol>
 *   <li>extraire les exigences fonctionnelles d'une specification ;</li>
 *   <li>deriver un cas de test JUnit 5 par exigence.</li>
 * </ol>
 *
 * <p><b>Temperature 0.0 sur les deux maillons.</b> C'est la condition de la
 * reproductibilite : dans une chaine, la variabilite du maillon 1 se propage
 * et s'amplifie au maillon 2. Un pipeline dont chaque etape derive n'est pas
 * un pipeline, c'est une loterie a deux tirages.</p>
 *
 * <p>La specification utilisee est celle des regles de remise NovaTech, la
 * meme que la Demo 2c et la Demo 6 : les stagiaires reconnaissent le domaine
 * et peuvent juger la pertinence des exigences extraites.</p>
 *
 * <p>Lancement :
 * {@code mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.Demo01Chaine"}</p>
 */
public final class Demo01Chaine {

    /**
     * La specification source, partagee avec les Demos 2c et 6.
     *
     * <p>Le fichier Markdown est PRODUIT par la Demo 2c : c'est la conversion
     * locale du {@code .docx}. Si la Demo 2c n'a pas encore ete jouee, il
     * n'existe pas — la classe le detecte et l'explique plutot que de lever
     * une {@code NoSuchFileException} nue.</p>
     */
    private static final Path SPEC = Path.of("data", "demo-02c", "specification-remises.md");

    /** Le binaire d'origine, present dans le depot, lui. */
    private static final Path SOURCE_DOCX = Path.of("data", "demo-02c", "specification-remises.docx");

    /** Temperature nulle : la chaine doit etre rejouable a l'identique. */
    private static final double TEMPERATURE = 0.0;

    private Demo01Chaine() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== Demo 1 — chaine de prompts, maillon par maillon ===");
        System.out.println("Modele : " + Llm.modeleChat());
        System.out.println("Source : " + SPEC);
        System.out.println();

        if (!Files.exists(SPEC)) {
            expliquerFichierManquant();
            return;
        }

        String spec = Files.readString(SPEC);
        System.out.printf("Specification chargee : %d caracteres, %d tokens%n%n",
                spec.length(), Llm.compterTokens(spec));

        // --- Maillon 1 : de la specification aux exigences -------------------
        System.out.println("--- Maillon 1 : extraire les exigences fonctionnelles ---");
        String exigences = Llm.chat(spec, """
                Extrais les exigences fonctionnelles de la spec, une par ligne,
                sans commentaire.""", TEMPERATURE);
        System.out.println(exigences);
        System.out.println();

        // --- Maillon 2 : des exigences aux cas de test -----------------------
        // L'entree de ce maillon est la SORTIE du precedent, pas la spec.
        // C'est ce qui fait la chaine : chaque etape travaille sur un materiau
        // deja reduit, donc moins de tokens et une tache mieux delimitee.
        System.out.println("--- Maillon 2 : deriver un cas de test par exigence ---");
        String casDeTest = Llm.chat(exigences, """
                Pour chaque exigence (une par ligne), propose un cas de test JUnit 5 :
                nom du test + objectif, format « nomDuTest : objectif ».""", TEMPERATURE);
        System.out.println(casDeTest);
        System.out.println();

        // --- Ce que la chaine a change ---------------------------------------
        System.out.println("--- Ce que le decoupage a change ---");
        System.out.printf("  Specification en entree du maillon 1 : %4d tokens%n",
                Llm.compterTokens(spec));
        System.out.printf("  Exigences en entree du maillon 2     : %4d tokens%n",
                Llm.compterTokens(exigences));
        System.out.println();
        System.out.println("  Le second maillon ne relit pas la specification : il travaille");
        System.out.println("  sur la liste produite par le premier. Chaque maillon a un prompt");
        System.out.println("  court, une tache unique, et se teste sur des entrees de reference.");
        System.out.println();
        System.out.println("  Le controle porte sur CHAQUE maillon. Si les cas de test sont");
        System.out.println("  mauvais, la question est : le maillon 2 a-t-il mal travaille, ou");
        System.out.println("  le maillon 1 lui a-t-il donne de mauvaises exigences ? Un prompt");
        System.out.println("  monolithique ne permet pas de poser cette question.");
    }

    /**
     * Message d'aide quand le Markdown source manque. Deux causes possibles,
     * et une seule des deux est une erreur de l'utilisateur.
     */
    private static void expliquerFichierManquant() {
        System.err.println("Fichier introuvable : " + SPEC);
        System.err.println("Repertoire courant  : " + Path.of("").toAbsolutePath());
        System.err.println();

        if (!Files.exists(SOURCE_DOCX)) {
            System.err.println("Le .docx source est introuvable lui aussi : cette classe doit");
            System.err.println("etre lancee depuis le repertoire java/ du depot.");
            System.err.println();
            System.err.println("  cd java");
            System.err.println("  mvn -q compile exec:java \\");
            System.err.println("    -Dexec.mainClass=\"fr.utopios.formation.demos.Demo01Chaine\"");
            return;
        }

        System.err.println("Le .docx est bien la, mais sa conversion en Markdown n'a pas encore");
        System.err.println("ete faite. C'est la Demo 2c qui la produit :");
        System.err.println();
        System.err.println("  source ~/.venv-formation/bin/activate");
        System.err.println("  cd data/demo-02c");
        System.err.println("  markitdown specification-remises.docx -o specification-remises.md");
        System.err.println("  cd ../..");
        System.err.println();
        System.err.println("Si markitdown est absent :");
        System.err.println("  pip install 'markitdown[docx,pptx,xlsx,pdf]'");
    }
}
