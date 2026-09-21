---
titre: "Exercice 1 — Mesurer ce qu'un prompt robuste apporte vraiment"
module: "Module 1 — Prompt engineering avancé"
duree: "30 minutes"
langage: "Java 21"
---

# Exercice 1 — Mesurer ce qu'un prompt robuste apporte vraiment

## Objectif pédagogique

Transformer un prompt vague en prompt professionnel, et surtout **prouver** le
gain par la mesure plutôt que de l'affirmer. Vous allez rejouer le même prompt
plusieurs fois et observer la dispersion des réponses.

## Prérequis

- Module 1 terminé (anatomie d'un prompt, anti-patterns).
- Le projet Maven `java/` de la formation, et Ollama démarré en local.
- Vérifiez votre environnement :

```bash
cd java && mvn -q compile exec:java \
  -Dexec.mainClass="fr.utopios.formation.commun.SmokeTest"
```

## Contexte

Un collègue pressé a écrit « Résume ce ticket. » Ça « marche un peu » : certains
jours la réponse est utile, d'autres non. Tant que nous en restons à une
impression, nous ne pouvons ni convaincre l'équipe ni industrialiser quoi que ce
soit.

Nous allons donc traiter la question comme une question d'ingénierie : **le
prompt robuste produit-il des sorties plus exploitables que le prompt naïf, et
comment le mesurons-nous ?**

Le critère que je vous propose est volontairement brutal : une sortie est
exploitable si **un programme sait la parser**. Si votre code ne sait pas lire la
réponse, aucun traitement en aval n'est possible.

## Matériel fourni

Le ticket de bug qui servira d'entrée commune aux deux versions du prompt. Il
contient volontairement du bruit et une information manquante :

```text
[BUG-4172] Panier vide apres paiement
Signale par : agence Lyon
Franchement ca fait trois fois cette semaine, c'est penible.
Quand un client paie par carte, la commande passe mais le panier
reste affiche avec les articles. Il recommande parfois deux fois.
Deux clients ont ete debites deux fois hier.
```

## Squelette de code à compléter

Créez `java/src/main/java/fr/utopios/formation/exercices/Exercice01.java` :

```java
package fr.utopios.formation.exercices;

import fr.utopios.formation.commun.Llm;
import java.util.LinkedHashSet;
import java.util.Set;

public class Exercice01 {

    private static final int TIRAGES = 3;
    private static final double TEMPERATURE = 0.7;

    private static final String TICKET = """
            [BUG-4172] Panier vide apres paiement
            Signale par : agence Lyon
            Franchement ca fait trois fois cette semaine, c'est penible.
            Quand un client paie par carte, la commande passe mais le panier
            reste affiche avec les articles. Il recommande parfois deux fois.
            Deux clients ont ete debites deux fois hier.
            """;

    public static void main(String[] args) {
        // ETAPE 1 : le prompt naif, tel que l'a ecrit le collegue.
        String promptNaif = "Resume ce ticket.\n\n" + TICKET;

        // ETAPE 2 : a vous. Redigez la version robuste en portant les quatre
        // briques : role, contexte, contraintes, format de sortie.
        // Le format doit etre VERIFIABLE PAR PROGRAMME (rubriques imposees).
        String systemeRobuste = "TODO";
        String promptRobuste  = "TODO";

        mesurer("NAIF", promptNaif, null);
        mesurer("ROBUSTE", promptRobuste, systemeRobuste);

        // ETAPE 4 : la variante a sortie structuree.
        // Utilisez Llm.chatJson(prompt, systeme) et validez VOUS-MEME le schema :
        // Ollama garantit la syntaxe JSON, jamais la presence des champs.
    }

    /** Rejoue le meme prompt et mesure la dispersion des reponses. */
    private static void mesurer(String etiquette, String prompt, String systeme) {
        Set<String> vues = new LinkedHashSet<>();
        int conformes = 0;

        for (int i = 1; i <= TIRAGES; i++) {
            String reponse = Llm.chat(prompt, systeme, TEMPERATURE).strip();
            vues.add(reponse);
            if (respecteFormat(reponse)) {
                conformes++;
            }
            System.out.printf("  %s tirage %d | %d caracteres%n",
                    etiquette, i, reponse.length());
        }
        System.out.printf("  %s -> %d reponses distinctes, %d/%d au format attendu%n",
                etiquette, vues.size(), conformes, TIRAGES);
    }

    // ETAPE 3 : ecrivez le controle de format correspondant a VOTRE prompt.
    private static boolean respecteFormat(String reponse) {
        return false; // TODO
    }
}
```

## Consignes, étape par étape

1. **Diagnostiquez.** Pour le prompt naïf, écrivez ce qui manque : rôle, contexte,
   contraintes, format de sortie. Une ligne par brique.
2. **Réécrivez.** Complétez `systemeRobuste` et `promptRobuste`. Imposez un format
   à rubriques fixes (par exemple `Probleme :`, `Impact :`, `Reproduction :`,
   `Information manquante :`).
3. **Codez le contrôle.** Implémentez `respecteFormat` pour vérifier la présence
   de vos rubriques. C'est ce qui rend la mesure objective.
4. **Mesurez.** Lancez et comparez les deux lignes de synthèse.
5. **Structurez.** Ajoutez la variante `Llm.chatJson` avec les champs `titre`,
   `severite` (énumération), `impact`, `etapes_reproduction`,
   `informations_manquantes`. **Validez le schéma dans votre code** : vérifiez que
   `severite` appartient bien à l'énumération.
6. **Concluez.** En une phrase : quel ajout a eu le plus d'impact, et pourquoi ?

## Commande de lancement

```bash
cd java && mvn -q compile exec:java \
  -Dexec.mainClass="fr.utopios.formation.exercices.Exercice01"
```

## Critères de réussite observables

- Le programme s'exécute et affiche les deux lignes de synthèse.
- Le prompt naïf obtient **0/3** au format attendu ; le prompt robuste **3/3**.
- La variante JSON produit les cinq champs, et votre validation confirme que
  `severite` est dans l'énumération.
- Vous savez expliquer pourquoi les deux prompts restent instables en texte brut
  alors qu'un seul est exploitable.

## Point de vigilance

Ne cherchez pas à obtenir des réponses **identiques** d'un tirage à l'autre : à
température 0.7 c'est impossible, et ce n'est pas l'objectif. Ce que nous
cherchons, c'est une **structure stable** : le contenu varie, la forme tient. Si
vous voulez la reproductibilité stricte, c'est la température 0.0 qu'il faut, et
nous verrons au module 8 pourquoi elle ne suffit pas non plus.

## Livrable attendu

Votre classe `Exercice01.java` complétée, la sortie console des deux mesures, et
vos trois à cinq lignes de conclusion sur l'ajout à plus fort impact.
