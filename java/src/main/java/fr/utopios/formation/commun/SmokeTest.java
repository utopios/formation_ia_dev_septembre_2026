package fr.utopios.formation.commun;

import java.util.List;

/** Verification de l'environnement : Ollama, chat, JSON, embeddings, tokens. */
public class SmokeTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(66));
        System.out.println("Verification de l'environnement Java + Ollama");
        System.out.println("=".repeat(66));
        System.out.println("Ollama      : " + Llm.BASE_URL);
        System.out.println("Modele chat : " + Llm.modeleChat());
        System.out.println("Modele embed: " + Llm.MODELE_EMBEDDING);

        System.out.println("\n[1/5] chat simple");
        String r = Llm.chat("Reponds en exactement trois mots : capitale de la France ?");
        System.out.println("      -> " + r.strip());

        System.out.println("\n[2/5] conversation multi-tours (memoire rejouee)");
        String suite = Llm.chat(List.of(
                Llm.Message.systeme("Tu reponds en une phrase courte."),
                Llm.Message.utilisateur("Mon service s'appelle Commandes."),
                Llm.Message.assistant("Bien note."),
                Llm.Message.utilisateur("Quel est le nom de mon service ?")), 0.0);
        System.out.println("      -> " + suite.strip());

        System.out.println("\n[3/5] sortie JSON contrainte");
        var json = Llm.chatJson(
                "Donne la ville et le pays de la tour Eiffel. "
                        + "Repond avec les cles \"ville\" et \"pays\".", null);
        System.out.println("      -> ville=" + json.path("ville").asText("?")
                + " pays=" + json.path("pays").asText("?"));

        System.out.println("\n[4/5] embeddings et similarite cosinus");
        double[] a = Llm.embedDocument("La remise commerciale est plafonnee a 15 %.");
        double[] b = Llm.embedQuery("Quel est le plafond de remise ?");
        double[] c = Llm.embedQuery("Quelle est la recette de la tarte aux pommes ?");
        System.out.println("      dimension        : " + a.length);
        System.out.printf ("      proche (remise)  : %.3f%n", Llm.cosine(a, b));
        System.out.printf ("      lointain (tarte) : %.3f%n", Llm.cosine(a, c));
        if (Llm.cosine(a, b) <= Llm.cosine(a, c)) {
            throw new IllegalStateException("La question proche devrait mieux scorer.");
        }

        System.out.println("\n[5/5] comptage de tokens du texte SEUL (raw)");
        System.out.println("      'Bonjour.'  -> " + Llm.compterTokens("Bonjour.") + " tokens");

        System.out.println("\nEnvironnement operationnel.");
    }
}
