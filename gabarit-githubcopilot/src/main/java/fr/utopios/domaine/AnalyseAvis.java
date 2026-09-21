package fr.utopios.copilotlab.domaine;

/** Resultat structure de l'analyse d'un avis client : le schema cible des quatre prompts. */
public record AnalyseAvis(Sentiment sentiment, String produit, String probleme, String pointPositif) {
}
