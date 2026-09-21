package fr.utopios.copilotlab.domaine;

import fr.utopios.copilotlab.json.ExtracteurJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mini-eval : la sortie du LLM contient-elle un JSON valide avec les 4 champs attendus ?
 * La validation vit dans le code, jamais dans la seule consigne du prompt.
 */
public final class ValidateurSchema {

    public static final List<String> CHAMPS_ATTENDUS =
            List.of("sentiment", "produit", "probleme", "point_positif");

    private final ExtracteurJson extracteur = new ExtracteurJson();

    public ResultatValidation valider(String sortieBrute) {
        Optional<Map<String, Object>> objet = extracteur.extraireObjet(sortieBrute);
        if (objet.isEmpty()) {
            return new ResultatValidation(0.0, "JSON invalide ou absent", List.of(), Optional.empty());
        }
        Map<String, Object> champs = objet.get();

        int presents = 0;
        for (String champ : CHAMPS_ATTENDUS) {
            if (champs.get(champ) instanceof String texte && !texte.isBlank()) {
                presents++;
            }
        }

        List<String> enTrop = new ArrayList<>(champs.keySet());
        enTrop.removeAll(CHAMPS_ATTENDUS);

        double score = (double) presents / CHAMPS_ATTENDUS.size();
        String diagnostic = presents + "/" + CHAMPS_ATTENDUS.size() + " champs presents";

        Optional<AnalyseAvis> analyse = Optional.empty();
        if (presents == CHAMPS_ATTENDUS.size()) {
            Optional<Sentiment> sentiment = Sentiment.depuis((String) champs.get("sentiment"));
            if (sentiment.isPresent()) {
                analyse = Optional.of(new AnalyseAvis(sentiment.get(),
                        (String) champs.get("produit"),
                        (String) champs.get("probleme"),
                        (String) champs.get("point_positif")));
            } else {
                diagnostic += ", sentiment hors valeurs autorisees";
            }
        }
        return new ResultatValidation(score, diagnostic, List.copyOf(enTrop), analyse);
    }
}
