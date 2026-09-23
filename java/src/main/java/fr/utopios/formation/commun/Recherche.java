package fr.utopios.formation.commun;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Briques de recherche partagees par les demos RAG (7, 9, 9b, 9c) et les
 * exercices du module 5 : le chunk et sa citation, le decoupage d'un Markdown
 * par sections, un BM25 minimal, la fusion de classements (RRF) et le rang
 * d'un resultat attendu.
 *
 * <p>Tout est en memoire et deterministe, sauf ce qui passe par {@link Llm}.
 * Le but est que chaque demo tienne sur un ecran : le decoupage et la mesure
 * sont ici, la pedagogie est dans la demo.</p>
 */
public final class Recherche {

    private Recherche() {
    }

    /** Un morceau indexable : d'ou il vient (la citation), et son texte. */
    public record Chunk(String document, String section, String texte) {
        public String citation() {
            return document + " › " + section;
        }
    }

    /** Un chunk et le score qui l'a classe. */
    public record Resultat(Chunk chunk, double score) {
    }

    // -----------------------------------------------------------------
    // Decoupage structurel d'un Markdown
    // -----------------------------------------------------------------

    private static final Pattern TITRE = Pattern.compile("(?m)^(#{1,3})\\s+(.+?)\\s*$");

    /**
     * Une section de titre = un chunk. Le niveau de titre retenu est le plus
     * haut qui apparait au moins trois fois : sur une page Confluence
     * exportee (« # Titre » puis « ## 1. … ») c'est le niveau 2 ; sur un docx
     * converti par markitdown (« # 1. … ») c'est le niveau 1. Ce qui precede
     * la premiere section (metadonnees de page) n'est pas indexe.
     */
    public static List<Chunk> sectionsMarkdown(String document, String contenu) {
        Map<Integer, Integer> parNiveau = new HashMap<>();
        Matcher m = TITRE.matcher(contenu);
        while (m.find()) {
            parNiveau.merge(m.group(1).length(), 1, Integer::sum);
        }
        int niveau = parNiveau.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .map(Map.Entry::getKey)
                .min(Integer::compareTo)
                .orElse(parNiveau.keySet().stream().min(Integer::compareTo).orElse(1));

        List<Chunk> chunks = new ArrayList<>();
        List<int[]> bornes = new ArrayList<>();
        List<String> titres = new ArrayList<>();
        m = TITRE.matcher(contenu);
        while (m.find()) {
            if (m.group(1).length() == niveau) {
                bornes.add(new int[] {m.start(), m.end()});
                titres.add(m.group(2));
            }
        }
        for (int i = 0; i < bornes.size(); i++) {
            int fin = i + 1 < bornes.size() ? bornes.get(i + 1)[0] : contenu.length();
            String texte = contenu.substring(bornes.get(i)[0], fin).replaceAll("\\n---\\s*$", "").strip();
            chunks.add(new Chunk(document, titres.get(i), texte));
        }
        return chunks;
    }

    // -----------------------------------------------------------------
    // Cosinus
    // -----------------------------------------------------------------

    /** Classe le corpus par similarite cosinus avec le vecteur de la question. */
    public static List<Resultat> classerCosinus(List<Chunk> corpus, List<double[]> vecteurs, double[] question) {
        List<Resultat> res = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            res.add(new Resultat(corpus.get(i), Llm.cosine(question, vecteurs.get(i))));
        }
        res.sort(Comparator.comparingDouble(Resultat::score).reversed());
        return res;
    }

    // -----------------------------------------------------------------
    // Lexical : tokenisation et BM25
    // -----------------------------------------------------------------

    private static final Set<String> MOTS_VIDES = Set.of(
            "le", "la", "les", "de", "des", "du", "un", "une", "et", "en", "au", "aux", "pour", "par",
            "sur", "dans", "est", "sont", "ce", "cette", "ces", "que", "qui", "quel", "quelle", "quels",
            "quelles", "ne", "pas", "plus", "se", "son", "sa", "ses", "il", "elle", "on", "ou", "a",
            "avec", "sans", "tout", "toute", "tous", "toutes", "si", "the", "of", "to", "and");

    /** Minuscules, sans accents, decoupe sur tout ce qui n'est pas lettre ou chiffre, sans mots vides. */
    public static List<String> tokeniser(String texte) {
        String plat = Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        List<String> mots = new ArrayList<>();
        for (String mot : plat.split("[^a-z0-9]+")) {
            if (mot.length() >= 2 && !MOTS_VIDES.contains(mot)) {
                mots.add(mot);
            }
        }
        return mots;
    }

    /**
     * BM25 : un terme rare pese plus (idf), un terme repete sature (k1), un
     * chunk long est penalise (b). Aucun modele, resultat identique a chaque
     * execution.
     */
    public static final class Bm25 {
        private static final double K1 = 1.2;
        private static final double B = 0.75;
        private final List<Chunk> corpus;
        private final List<List<String>> tokens;
        private final Map<String, Integer> df = new HashMap<>();
        private final double longueurMoyenne;

        public Bm25(List<Chunk> corpus) {
            this.corpus = corpus;
            this.tokens = corpus.stream().map(c -> tokeniser(c.texte())).toList();
            this.longueurMoyenne = tokens.stream().mapToInt(List::size).average().orElse(1);
            for (List<String> t : tokens) {
                for (String mot : new LinkedHashSet<>(t)) {
                    df.merge(mot, 1, Integer::sum);
                }
            }
        }

        public List<Resultat> classer(String question) {
            int n = corpus.size();
            List<Resultat> res = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                List<String> doc = tokens.get(i);
                double score = 0;
                for (String terme : new LinkedHashSet<>(tokeniser(question))) {
                    int dfT = df.getOrDefault(terme, 0);
                    if (dfT == 0) {
                        continue;
                    }
                    long tf = doc.stream().filter(terme::equals).count();
                    double idf = Math.log((n - dfT + 0.5) / (dfT + 0.5) + 1);
                    double norme = K1 * (1 - B + B * doc.size() / longueurMoyenne);
                    score += idf * (tf * (K1 + 1)) / (tf + norme);
                }
                res.add(new Resultat(corpus.get(i), score));
            }
            res.sort(Comparator.comparingDouble(Resultat::score).reversed());
            return res;
        }
    }

    /** Part des termes de la question presents dans le chunk (0 a 1) : le recouvrement lexical du TP 4. */
    public static double recouvrement(String question, Chunk chunk) {
        Set<String> q = new LinkedHashSet<>(tokeniser(question));
        if (q.isEmpty()) {
            return 0;
        }
        Set<String> c = new LinkedHashSet<>(tokeniser(chunk.texte()));
        long communs = q.stream().filter(c::contains).count();
        return (double) communs / q.size();
    }

    // -----------------------------------------------------------------
    // Fusion de classements
    // -----------------------------------------------------------------

    /**
     * Reciprocal Rank Fusion : chaque chunk recoit 1/(k + rang) dans chaque
     * classement, et on somme. On fusionne des RANGS, jamais des scores : un
     * cosinus (0,4-0,8) et un BM25 (0-15) ne sont pas comparables.
     */
    @SafeVarargs
    public static List<Resultat> rrf(int k, List<Resultat>... classements) {
        Map<Chunk, Double> fusion = new LinkedHashMap<>();
        for (List<Resultat> classement : classements) {
            for (int rang = 0; rang < classement.size(); rang++) {
                fusion.merge(classement.get(rang).chunk(), 1.0 / (k + rang + 1), Double::sum);
            }
        }
        return fusion.entrySet().stream()
                .map(e -> new Resultat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Resultat::score).reversed())
                .toList();
    }

    // -----------------------------------------------------------------
    // Mesure
    // -----------------------------------------------------------------

    /** Rang (1..max) du premier chunk qui satisfait le critere ; 0 s'il n'est pas dans les max premiers. */
    public static int rang(List<Resultat> classement, Predicate<Chunk> attendu, int max) {
        for (int i = 0; i < Math.min(max, classement.size()); i++) {
            if (attendu.test(classement.get(i).chunk())) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Ecart entre le premier et le deuxieme score : la robustesse du classement. */
    public static double marge(List<Resultat> classement) {
        return classement.size() < 2 ? 0 : classement.get(0).score() - classement.get(1).score();
    }

    public static String tronquer(String s, int max) {
        String plat = s.replace('\n', ' ').replaceAll("\\s+", " ").strip();
        return plat.length() <= max ? plat : plat.substring(0, max - 1) + "…";
    }
}
