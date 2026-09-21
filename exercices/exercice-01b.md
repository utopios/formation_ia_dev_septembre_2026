---
titre: "Exercice 1b — Du prompt jetable au gabarit d'équipe, dans GitHub Copilot"
module: "Module 2 — Analyse multi-formats & GitHub Copilot"
duree: "35 minutes"
langage: "Java 21 — GitHub Copilot, VS Code de bureau"
jour: "Jour 1"
---

# Exercice 1b — Du prompt jetable au gabarit d'équipe, dans GitHub Copilot

> **Où se fait cet exercice : sur votre poste, dans VS Code de bureau.**
> Pas dans le workspace du serveur — Copilot n'y fonctionne pas.

## Objectif pédagogique

Appliquer à GitHub Copilot ce que le module 01 a établi sur Ollama : **un
prompt n'est pas une question, c'est un artefact d'ingénierie** — et un
artefact d'ingénierie se versionne.

Vous allez encoder dans le dépôt ce que vous auriez sinon retapé à chaque
requête : le rôle, le contexte, les contraintes, le format de sortie. Les
quatre composantes du module 01, rangées dans des fichiers que toute l'équipe
partage.

## Prérequis

- Le dépôt `atelier-copilot` cloné ce matin sur votre poste, `mvn clean test`
  au vert (45 tests).
- GitHub Copilot actif dans VS Code de bureau.

Vous n'avez besoin ni d'Ollama, ni de pgvector, ni d'un conteneur.

## Contexte

`atelier-copilot` est la copie de travail du tunnel de commande B2B de
**NovaTech Industries** : panier, remises, validation hiérarchique,
transmission à la facturation. Vingt-cinq classes que vous n'avez jamais lues.

C'est la situation d'une arrivée sur un projet. La question n'est pas « est-ce
que Copilot sait coder » — il sait. La question est : **sait-il coder comme
votre équipe code ?**

## Étape 1 — Découvrir le terrain, et vérifier (10 min)

Avant d'écrire des règles, il faut savoir lesquelles. Ouvrez le dépôt dans
VS Code et posez, **en mode Ask** avec `@workspace` :

```text
@workspace Ou est implemente le plafond de remise, quelle est sa valeur, et
quelles classes le contournent ou le recalculent ?
```

Puis **contrôlez la réponse** par un moyen déterministe, hors Copilot :

```bash
grep -rn 'new BigDecimal("15")' src/main/java --include="*.java"
```

Le `grep` remonte **deux** classes portant la valeur 15 :

| Classe | Constante | Rôle |
|---|---|---|
| `DiscountPolicy` | `MAX_DISCOUNT_RATE` | plafond du taux de remise |
| `DiscountValidationService` | `SALES_DIRECTOR_THRESHOLD` | seuil de délégation du directeur commercial |

Le même nombre, deux significations métier différentes, **aucune des deux ne
référençant l'autre**. Copilot cite rarement les deux spontanément.

Ouvrez aussi `OrderService.applyCommercialGesture` : un calcul monétaire y
passe par un `double` alors que tout le reste du domaine est en `BigDecimal`.

> **Ce que vous venez d'apprendre est exactement ce qu'il faut encoder.** Un
> assistant qui ignore la règle des 15 % et la convention `BigDecimal` produira
> du code plausible et faux. Un assistant à qui on l'a dit **une fois, dans un
> fichier versionné**, ne l'oublie plus.

## Étape 2 — Le problème, avant la solution (3 min)

Ouvrez un **nouveau fil** de chat et demandez, sans rien avoir configuré :

```text
Ajoute une methode qui applique une remise exceptionnelle a une commande.
```

Lisez la réponse **sans l'appliquer**. Notez trois choses :

1. Le type utilisé pour les montants — `double` ou `BigDecimal` ?
2. Le plafond de 15 % est-il vérifié ?
3. La validation hiérarchique est-elle évoquée ?

Gardez cette réponse sous les yeux : c'est votre point de comparaison.

## Étape 3 — Les instructions permanentes (8 min)

Créez `.github/copilot-instructions.md` à la racine du dépôt. Ce fichier est lu
**à chaque requête, sur tout le dépôt**.

Structurez-le selon les quatre composantes du module 01 :

```markdown
# Conventions du projet orders-api

<!-- CONTEXTE : ce que le modèle doit savoir du projet -->
Java 21, Maven. Architecture en couches : web -> service -> domain -> infrastructure.

<!-- CONTRAINTES : les règles non négociables -->
## Règles non négociables

- Tous les montants sont des `BigDecimal`, jamais des `double`.
- ...

<!-- CONTEXTE MÉTIER : ce que le grep de l'étape 1 vous a appris -->
## Règles métier structurantes

- ...

<!-- FORMAT : comment le modèle doit répondre -->
## Format des réponses

- ...
```

**À vous de le remplir.** Les commentaires HTML guident, ils ne restent pas.
Trois exigences :

- la règle `BigDecimal` doit y être **avec sa raison** — les erreurs d'arrondi
  sur des montants facturés ne sont pas acceptables. Une règle sans
  justification se contourne ;
- le plafond de 15 % doit y être, **en précisant qu'il vit dans
  `DiscountPolicy`** et que personne d'autre ne le recalcule ;
- le fait que `DiscountValidationService` porte un seuil homonyme mais distinct.

> **Piège à éviter** : ne recopiez pas la spécification métier entière. Ce
> fichier part à **chaque** requête. Ce qui est permanent doit être court et
> structurant ; le reste ira dans des instructions conditionnelles.

**Mesurez.** Rouvrez un nouveau fil, reposez la question de l'étape 2 à
l'identique, comparez les trois points notés. C'est l'avant/après du module 01,
sur votre outil de tous les jours.

## Étape 4 — Les instructions conditionnelles (7 min)

Tout ne vaut pas partout. Créez `.github/instructions/tests.instructions.md` :

```markdown
---
name: 'Conventions de test'
applyTo: '**/*Test.java'
---

<vos conventions de test ici>
```

Le `applyTo` est un **motif glob** : ces instructions ne se déclenchent que sur
les fichiers correspondants. C'est la décomposition du module 01 appliquée au
contexte — on ne charge pas le modèle de ce qui ne concerne pas sa tâche.

Lisez `DiscountPolicyTest` pour en **déduire** les conventions réelles du
projet plutôt que d'inventer les vôtres : nommage des méthodes, bibliothèque
d'assertions, granularité des cas.

**Démontrez que le ciblage fonctionne**, en deux requêtes :

1. « Ajoute un test pour le plafond de remise » avec `DiscountPolicyTest.java`
   ouvert → les conventions s'appliquent.
2. La même demande avec `DiscountPolicy.java` ouvert → elles ne s'appliquent
   pas.

Si vous ne voyez aucune différence, votre `applyTo` ne correspond à rien :
vérifiez le motif.

## Étape 5 — Le gabarit devient une commande (7 min)

C'est l'aboutissement. Votre gabarit du TP 1 est encore un fichier texte que
vous copiez-collez. Transformez-le en **commande exécutable**.

Créez `.github/prompts/revue-remise.prompt.md` :

```markdown
---
name: revue-remise
agent: agent
description: Revue d'une modification touchant au calcul de remise
argument-hint: le focus de la revue
tools: ['search/codebase', 'changes']
---

<votre gabarit : rôle, contexte, contraintes, format de sortie>

Vérifie en priorité : ${input:focus:respect du plafond de 15 %}.
```

Quatre éléments à ne pas manquer :

- **`${input:focus:...}`** — le paramètre et sa valeur par défaut. C'est ce qui
  rend le gabarit réutilisable plutôt que figé.
- **`tools:`** — la liste des outils autorisés. Nous y reviendrons demain : ce
  n'est pas une consigne polie, c'est une **restriction de capacité**.
- **Un format de sortie contraint** — par exemple une ligne par remarque,
  préfixée de `[BLOQUANT]`, `[SECURITE]`, `[SUGGESTION]` ou `[QUESTION]`. Le
  module 01 l'a établi : une sortie exploitable est une sortie qu'un programme
  saurait parser.
- **L'interdiction de supposer** — « ne signale que des points fondés sur le
  diff réel, jamais sur une supposition ».

Testez : modifiez une ligne dans `DiscountPolicy`, puis tapez `/revue-remise`
dans le chat. VS Code demande le focus, puis lance la revue.

## Résultat attendu

- **Étape 1** : le `grep` remonte deux classes, Copilot en cite souvent une.
- **Étape 3** : entre l'avant et l'après, le type des montants passe à
  `BigDecimal` et le plafond de 15 % apparaît dans la réponse. Sinon, vos
  instructions sont trop vagues ou trop longues — resserrez.
- **Étape 4** : les conventions de test se déclenchent sur `*Test.java` et pas
  ailleurs. La différence doit être **visible**, pas supposée.
- **Étape 5** : `/revue-remise` apparaît dans le chat et produit une sortie au
  format imposé.

Trois fichiers en fin d'exercice, tous versionnables :

```
.github/copilot-instructions.md
.github/instructions/tests.instructions.md
.github/prompts/revue-remise.prompt.md
```


