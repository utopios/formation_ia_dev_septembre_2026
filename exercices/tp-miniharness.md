---
titre: "TP 2 — Compléter un mini-harness agentique outillé"
module: "Module 3 — Agents IA : tool calling, harness, MCP"
jour: "Jour 3"
---

# TP 2 — Compléter un mini-harness agentique outillé

## Objectif pédagogique

Démonter la boucle d'un agent outillé en l'écrivant vous-même : comprendre la
demande, appeler un outil, exploiter le résultat, produire la réponse finale.
Vous allez constater qu'un agent n'est pas un modèle plus malin — c'est une
**boucle que vous écrivez**, autour d'un modèle qui ne sait que renvoyer du
texte.

## Contexte métier

On vous demande un assistant capable de répondre à des questions sur un projet
Java : quels fichiers le composent, que fait tel module. Le modèle ne connaît
évidemment pas votre code. Il faut donc lui donner des **outils** pour
l'observer — et c'est vous qui décidez de ce qu'il a le droit de voir.

Deux exigences que le client pose d'emblée, et qui sont toutes deux de votre
ressort, pas de celui du modèle :

- **moindre privilège** : l'agent lit dans le projet, et nulle part ailleurs ;
- **robustesse** : `llama3.2:1b` ne dispose pas d'API de tool calling native
  fiable. Il produira du JSON approximatif, ou demandera un outil qui n'existe
  pas. La boucle doit y survivre, pas espérer la perfection.

## Prérequis

- Module 3 terminé (tool calling, principe du harness, traces).
- Ollama avec `llama3.2:1b`.
- Le projet Maven `java/` et `commun/Llm.java`.

## API à votre disposition

- `Llm.chat(List<Llm.Message> messages, double temperature)` — conversation
  multi-tours. Le modèle n'a **aucune mémoire** : c'est vous qui rejouez tout
  l'historique à chaque tour.
- `Llm.Message.systeme(...)`, `Llm.Message.utilisateur(...)`,
  `Llm.Message.assistant(...)`.

## Travail demandé

### Étape 1 — Le bac à sable

Écrivez `resoudreDansBacASable(String chemin)`, qui résout un chemin relatif et
garantit qu'il reste sous la racine autorisée.

> L'ordre des opérations est le point critique : normalisez **d'abord** (ce qui
> aplatit les `..`), vérifiez **ensuite**. Vérifier avant de normaliser laisse
> passer `src/../../../etc/passwd`. C'est la traversée de répertoire classique ;
> un agent ne change rien au problème, il l'expose simplement à un générateur de
> texte non déterministe.

### Étape 2 — Les deux outils

- `lister_fichiers(sous_dossier)` — les fichiers du projet, chemins relatifs.
- `lire_fichier(chemin)` — le contenu d'un fichier texte.

Les deux renvoient un message d'erreur exploitable (jamais une exception qui
remonte) en cas de chemin refusé ou de fichier absent. **Bornez** ce que vous
renvoyez : un fichier de 3 000 lignes réinjecté dans la conversation sature le
contexte du petit modèle et lui fait perdre le fil.

### Étape 3 — Le protocole

Faute de tool calling natif, imposez un protocole JSON dans le prompt système :

```json
{"outil": "lister_fichiers", "arguments": {"sous_dossier": "."}}
{"reponse": "le texte de la réponse finale"}
```

### Étape 4 — L'extraction défensive

Écrivez `extraireJson(String texte)`. Le petit modèle encadre souvent son JSON
d'un bloc Markdown, ou ajoute une phrase avant. Allez chercher le premier `{` et
le dernier `}` plutôt que d'exiger la perfection. C'est ce qui fait la différence
entre un agent qui tourne et un agent qui plante au tour 1.

### Étape 5 — La boucle

Implémentez `executer(String demande)`, bornée à 6 tours, qui distingue un appel
d'outil d'une réponse finale, exécute l'outil, réinjecte son résultat dans la
conversation et recommence.

Traitez explicitement **quatre cas de robustesse** :

1. JSON illisible → recadrer le modèle, ne pas planter ;
2. outil inconnu → renvoyer l'erreur **avec la liste des outils réels**, ce qui
   permet au modèle de se rattraper au tour suivant ;
3. outil qui lève une exception → la capturer ;
4. **objet contenant à la fois `outil` et `reponse`**. Celui-là, vous le
   rencontrerez : `llama3.2:1b` annonce régulièrement un appel d'outil et
   invente son résultat dans la foulée. Si vous lisez `reponse` en premier, vous
   acceptez une réponse fabriquée sans qu'aucun fichier n'ait été lu — et votre
   agent hallucine en silence. **L'appel d'outil est prioritaire** ;
5. **la boucle stérile**. Celui-là aussi, vous le rencontrerez, et c'est le plus
   instructif : le modèle redemande le **même outil avec les mêmes arguments**,
   tour après tour, sans jamais conclure. Une simple limite de tours ne suffit
   pas — elle transforme un bug en échec silencieux. Mémorisez la signature
   `outil|arguments` des appels déjà servis ; si l'agent répète un appel, c'est
   qu'il dispose déjà du résultat : exigez alors une conclusion.

### Étape 6 — Les traces

Journalisez chaque tour : quel outil, quels arguments, quel volume de résultat.
C'est votre observabilité minimale, et c'est elle qui vous permettra de répondre
à la question « pourquoi l'agent a-t-il mis quatre tours ? ».

## Commande

```bash
cd java
mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.tp.Tp02Solution"
```

## Critères de réussite observables

- [ ] `lire_fichier("../../../../etc/passwd")` renvoie un refus explicite. Cette
      vérification passe **sans appeler le modèle** : c'est une propriété de
      votre code, pas du LLM.
- [ ] L'agent appelle effectivement au moins un outil avant de répondre. Un agent
      qui répond sans avoir rien lu a halluciné, même si sa réponse tombe juste.
- [ ] La boucle converge en moins de 6 tours et affiche le journal des appels.
- [ ] Le programme échoue (exception) si l'agent n'a appelé aucun outil, ou s'il
      n'a pas convergé.

## Question à laquelle vous devrez répondre

Regardez la taille du prompt à chaque tour (`Llm.derniersTokensPrompt()`).
Pourquoi croît-elle si vite, et qu'est-ce que cela implique pour un agent qui
tournerait pendant trente tours ?
