package fr.utopios.copilotlab.domaine;

import java.util.Locale;
import java.util.Optional;

/** Valeurs autorisees pour le champ {@code sentiment} du schema d'extraction. */
public enum Sentiment {
    POSITIF, MOYEN, NEGATIF;

    /** Convertit une valeur brute de LLM ("Positif", " négatif ") en enum, sans lever d'exception. */
    public static Optional<Sentiment> depuis(String brut) {
        if (brut == null) {
            return Optional.empty();
        }
        String normalise = brut.strip().toLowerCase(Locale.ROOT)
                .replace('é', 'e');
        return switch (normalise) {
            case "positif" -> Optional.of(POSITIF);
            case "moyen", "neutre", "mitige" -> Optional.of(MOYEN);
            case "negatif" -> Optional.of(NEGATIF);
            default -> Optional.empty();
        };
    }
}
