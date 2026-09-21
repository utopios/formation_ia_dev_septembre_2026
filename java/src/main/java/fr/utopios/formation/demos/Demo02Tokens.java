public final class Demo02Tokens {

    private static final Path DOSSIER = Path.of("data/demo-02c");

    /** Les trois paires binaire / Markdown produites par la Demo 2c. */
    private static final String[][] PAIRES = {
            {"specification-remises.docx", "specification-remises.md"},
            {"comite-tarifaire.pptx", "comite-tarifaire.md"},
            {"bareme-remises.xlsx", "bareme-remises.md"},
    };

    private Demo02Tokens() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== Demo 2 — mesurer l'economie, plutot que la supposer ===");
        System.out.println();

        if (!Files.isDirectory(DOSSIER)) {
            System.err.println("Dossier introuvable : " + DOSSIER.toAbsolutePath());
            System.err.println("Repertoire courant  : " + Path.of("").toAbsolutePath());
            System.err.println();
            System.err.println("Cette classe se lance depuis le repertoire java/ du depot :");
            System.err.println();
            System.err.println("  cd java");
            System.err.println("  mvn -q compile exec:java \\");
            System.err.println("    -Dexec.mainClass=\"fr.utopios.formation.demos.Demo02Tokens\"");
            return;
        }

        // Les .md sont PRODUITS par la Demo 2c. Sans eux, le tableau serait
        // vide et l'utilisateur n'aurait aucune idee de ce qui manque.
        long markdownPresents = 0L;
        for (String[] paire : PAIRES) {
            if (Files.exists(DOSSIER.resolve(paire[1]))) {
                markdownPresents++;
            }
        }
        if (markdownPresents == 0L) {
            expliquerConversionManquante();
            return;
        }

        long totalBinaire = 0L;
        long totalMarkdown = 0L;
        int totalTokens = 0;

        System.out.printf("%-30s %10s %10s %8s %9s%n",
                "Fichier", "binaire", "markdown", "ratio", "tokens");
        System.out.println("-".repeat(70));

        for (String[] paire : PAIRES) {
            Path binaire = DOSSIER.resolve(paire[0]);
            Path markdown = DOSSIER.resolve(paire[1]);

            if (!Files.exists(markdown)) {
                System.out.printf("%-30s  Markdown absent — lancer markitdown d'abord%n",
                        paire[0]);
                continue;
            }

            long octetsBinaire = Files.size(binaire);
            long octetsMarkdown = Files.size(markdown);
            int tokens = Llm.compterTokens(Files.readString(markdown));

            totalBinaire += octetsBinaire;
            totalMarkdown += octetsMarkdown;
            totalTokens += tokens;

            System.out.printf("%-30s %10d %10d %7.1fx %9d%n",
                    paire[0], octetsBinaire, octetsMarkdown,
                    (double) octetsBinaire / octetsMarkdown, tokens);
        }

        System.out.println("-".repeat(70));
        if (totalMarkdown > 0) {
            System.out.printf("%-30s %10d %10d %7.1fx %9d%n",
                    "TOTAL", totalBinaire, totalMarkdown,
                    (double) totalBinaire / totalMarkdown, totalTokens);
        }

        System.out.println();
        System.out.println("--- Ce que ces chiffres disent ---");
        System.out.println();
        System.out.println("  Le ratio d'octets n'est pas le ratio de cout : ce qui est facture,");
        System.out.println("  ce sont les tokens. La derniere colonne est donc la vraie mesure.");
        System.out.println();
        System.out.printf("  Les trois documents reunis representent %d tokens de Markdown.%n",
                totalTokens);
        System.out.println("  Dans une fenetre de contexte de 8 000 tokens, cela laisse encore");
        System.out.println("  la place aux instructions, a la question et a la reponse.");
        System.out.println();
        System.out.println("  Un binaire, lui, n'a PAS de nombre de tokens : il faut d'abord");
        System.out.println("  que quelque chose le convertisse en texte. Glisser un .docx dans");
        System.out.println("  un chat, c'est deleguer cette conversion a un outil qui ne dit ni");
        System.out.println("  comment il s'y prend, ni ce qu'il a retenu — ni ce qu'il a emporte");
        System.out.println("  au passage (voir les metadonnees, Demo 2c).");
    }

    /**
     * Message d'aide quand aucun Markdown n'a encore ete produit. Les binaires
     * sont versionnes dans le depot, les .md non : ils sont le RESULTAT de la
     * conversion locale, qui est precisement le geste enseigne par la Demo 2c.
     */
    private static void expliquerConversionManquante() {
        System.err.println("Aucun fichier Markdown dans " + DOSSIER + ".");
        System.err.println();
        System.err.println("Les .docx, .pptx et .xlsx sont versionnes ; les .md ne le sont pas :");
        System.err.println("ils sont le resultat de la conversion locale, le geste meme qu'enseigne");
        System.err.println("la Demo 2c. Produisez-les d'abord :");
        System.err.println();
        System.err.println("  source ~/.venv-formation/bin/activate");
        System.err.println("  cd data/demo-02c");
        System.err.println("  for f in *.docx *.pptx *.xlsx; do markitdown \"$f\" -o \"${f%.*}.md\"; done");
        System.err.println("  cd ../..");
        System.err.println();
        System.err.println("Si markitdown est absent (les crochets sont necessaires) :");
        System.err.println("  pip install 'markitdown[docx,pptx,xlsx,pdf]'");
    }
}