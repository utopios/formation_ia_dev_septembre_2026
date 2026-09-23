package fr.utopios.formation.demos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public final class Donnees {

    private Donnees() {
        // Classe utilitaire.
    }

    /**
     * Renvoie le chemin d'un fichier ou dossier sous {@code java/data/}.
     *
     * @param relatif chemin relatif a data/, par exemple "demo-02/incident/app.log"
     */
    public static Path chemin(String relatif) {
        return racineDonnees().resolve(relatif);
    }

    /** Lit un fichier texte de data/ en UTF-8. */
    public static String lire(String relatif) {
        try {
            return Files.readString(chemin(relatif), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "Fichier de donnees introuvable ou illisible : " + relatif, e);
        }
    }

    /** Lit un fichier texte de data/ ligne par ligne. */
    public static List<String> lireLignes(String relatif) {
        try {
            return Files.readAllLines(chemin(relatif), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "Fichier de donnees introuvable ou illisible : " + relatif, e);
        }
    }

    /**
     * Remonte depuis le repertoire courant jusqu'au dossier {@code data/}.
     *
     * <p>On tente aussi {@code java/data} : c'est le cas ou l'on lance la JVM
     * depuis la racine du depot de formation et non depuis le module Maven.</p>
     */
    private static Path racineDonnees() {
        Path courant = Path.of("").toAbsolutePath();
        for (Path p = courant; p != null; p = p.getParent()) {
            Path direct = p.resolve("data");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            Path sousJava = p.resolve("java").resolve("data");
            if (Files.isDirectory(sousJava)) {
                return sousJava;
            }
        }
        throw new IllegalStateException(
                "Dossier data/ introuvable depuis " + courant
                        + ". Lancez la demo depuis le dossier java/ du depot.");
    }
}
