package fr.utopios.copilotlab.domaine;

import java.util.List;
import java.util.Optional;

/**
 * Verdict du validateur : un score entre 0 et 1, un diagnostic lisible, les cles en trop,
 * et l'analyse typee quand tout est conforme.
 */
public record ResultatValidation(double score, String diagnostic, List<String> clesEnTrop,
                                 Optional<AnalyseAvis> analyse) {
}
