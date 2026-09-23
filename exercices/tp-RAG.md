---
titre: "TP L'assistant documentaire de l'équipe Commandes : un RAG construit palier par palier"
module: "Module 5 — Fondations RAG"
langage: "Java 21"
jour: "Jour 3"
---

# TP  L'assistant documentaire de l'équipe Commandes : un RAG construit palier par palier

## Objectif pédagogique

Construire, sur le **vrai corpus** de NovaTech, l'assistant que l'équipe
Commandes demande — et le faire **évoluer** en cinq paliers, chacun répondant à
un problème constaté au palier précédent, chacun **mesuré** sur le même jeu de
22 questions. À la fin, vous avez un tableau : palier → hit@1, hit@3, MRR,
réponses fondées, refus, fuites. C'est ce tableau qui dit ce que chaque
décision a apporté, pas votre impression.

## Le scénario

Sophie, référente technique de l'équipe Commandes, en a assez :

> « On a tout : la spec dans Confluence, le backlog dans Jira, le code dans
> GitLab, et les documents de la direction commerciale sur le partage. Personne
> ne sait où chercher. Un nouveau met une semaine à trouver que le plafond est
> à 15 %. Je veux un assistant qui répond **avec la source**, qui dit « je ne
> sais pas » quand on n'a rien, et qui **ne montre pas** aux commerciaux le
> compte rendu du comité tarifaire — il y a des choses non tranchées dedans. »

Trois exigences, trois choses qu'on mesure : la réponse est fondée, le refus
est exact, rien ne fuit.

## Le corpus fourni — `java/data/tp-04b/`

| Dossier | Contenu | Accès |
|---|---|---|
| `confluence/` | les 4 pages de l'espace NOVA (spécification, règles de remise, normes, architecture), 35 sections | tous |
| `jira/` | les 14 tickets du projet NOVA, avec descriptions, critères, trace d'erreur | tous |
| `code/` | 5 classes d'`orders-api` : `DiscountPolicy`, `DiscountValidationService`, `Cart`, `OrderService`, `SageInvoicingClient` | `developpeur` |
| `direction-commerciale/` | la spécification des remises (docx), le comité tarifaire (pptx), le barème (xlsx) — avec leur conversion Markdown | `direction` seulement |

Et `questions.json` : 22 questions. 15 ouvertes à tous, 3 dont la réponse n'est
que dans les documents de la direction, 4 **sans réponse dans le corpus**.
Chaque question porte la source et la section attendues (pour mesurer la
recherche) et un fragment de réponse attendu (pour mesurer la génération).
Lisez `README.md` dans le dossier : il décrit le format.

Quelques-unes, pour situer :

| # | Question | Où est la réponse |
|---|---|---|
| 1 | Qui doit valider une remise de 13 % ? | Règles de remise, § Seuils de délégation |
| 4 | Comment doit être nommé un fichier de migration Flyway ? | Normes, § Conventions de nommage |
| 11 | Quelle est la cause de l'erreur 500 lors du recalcul du panier ? | Jira, ticket 5 |
| 13 | Quelle constante du code porte le plafond de remise ? | `DiscountPolicy.java` |
| 16 | Quand le barème de remise doit-il être revu ? | Comité tarifaire (pptx) — **direction seulement** |
| 17 | Quel code identifie la catégorie Grand compte dans le fichier Excel du barème ? | Barème (xlsx) — **direction seulement** ; Confluence dit `KEY_ACCOUNT`, le fichier dit `GRAND_COMPTE` |
| 19 | Quel est le délai de livraison standard d'une commande ? | nulle part |

## Prérequis

- Module 5 vu jusqu'aux démos RAG 04 à 07 ; Exercice 5 fait.
- Ollama avec `nomic-embed-text` et `llama3.2:3b` : `export OLLAMA_CHAT_MODEL=llama3.2:3b`.
- Le projet `java/`. Tout est en mémoire ; pgvector n'est pas nécessaire
  (vous pouvez l'utiliser au palier 2 si vous voulez persister).

## API à votre disposition

- `Llm.embedDocuments(List<String>)`, `Llm.embedQuery(String)`, `Llm.cosine(...)`,
  `Llm.chat(prompt, systeme, temperature)`.
- `Recherche` (`fr.utopios.formation.commun`) : `Chunk(document, section, texte)`
  et sa `citation()`, `sectionsMarkdown(document, contenu)`, `classerCosinus`,
  `Bm25`, `recouvrement`, `rrf`, `rang`, `marge`.
- `Donnees.chemin("tp-04b/...")`, `Donnees.lire(...)`.

Vous pouvez tout réécrire ; vous n'êtes pas obligés de tout réécrire.

## Le harnais de mesure — à écrire au palier 1, à garder jusqu'au bout

Avant toute technique, écrivez ce qui mesure. Pour chaque palier :

- **Recherche**, sur les 18 questions avec réponse : le rang du chunk attendu
  dans le top 10 (bonne source **et** bonne section, ou texte contenant la
  réponse attendue), puis hit@1, hit@3, MRR.
- **Génération**, sur les 22 : une réponse est **fondée** si elle contient le
  fragment attendu **et** cite un `[Sn]` dont le passage le contient ; un refus
  est **exact** s'il reproduit la phrase de refus mot pour mot. Comparez sans
  accents ni majuscules.
- **Fuite** (palier 5) : un passage de `direction-commerciale/` dans le top k
  d'une requête faite par un autre rôle que `direction`.

Le prompt système impose trois choses : passages seuls, citation `[Sn]` après
chaque fait, phrase de refus exacte —
`Je ne trouve pas cette information dans les documents fournis.`

## Les cinq paliers

### Palier 1 — Le socle naïf (40 min)

Indexez **tout** le corpus (`.md` et `.java`, pas les binaires Office) coupé à
500 caractères, sans regarder le contenu. Cosinus, top 3, génération sourcée.
Mesurez.

Ce que vous devez constater : ça marche « à peu près ». Regardez **où** ça
échoue : la coupe tombe au milieu d'un tableau du docx, d'une méthode Java,
d'une trace d'erreur Jira ; la citation dit « morceau 7 » et personne ne peut
la vérifier.

### Palier 2 — Le chunking par source (35 min)

Chaque source découpée comme elle est écrite : une section Confluence, **un
ticket Jira**, **une méthode Java avec sa javadoc**, une diapositive du pptx, une
feuille du xlsx. Les tableaux restent entiers. La citation devient lisible :
`confluence/02-regles-de-remise.md › 4. Seuils de délégation`,
`code/DiscountPolicy.java › DiscountPolicy · public static final BigDecimal MAX_DISCOUNT_RATE`.

Mesurez. Exigence : **aucune question ne doit régresser** par rapport au
palier 1 ; si une régresse, dites laquelle et pourquoi avant de continuer.

### Palier 3 — Le contexte et l'hybride (30 min)

Deux compléments gratuits : « Source : … / Section : … » **dans le texte
vectorisé** ; et BM25 fusionné au cosinus par RRF. Mesurez. Regardez la
question 4 (Flyway) et la question 13 (`MAX_DISCOUNT_RATE`) : ce sont les mots
rares que l'embedding noie et que le lexical voit.

### Palier 4 — Le reranking (25 min)

Élargissez à 8, relisez avec le juge pondéré (`0,6 × cosinus + 0,4 ×
recouvrement`), ne passez que 3 passages à la génération. Mesurez la recherche
**et** la génération : c'est le palier où « réponses fondées » doit monter,
parce que le bon passage est plus souvent `[S1]`.

Trouvez au moins une question où le reranking fait **moins bien** que le
palier 3, et expliquez-la.

### Palier 5 — Le cloisonnement (30 min)

Trois rôles : `developpeur` (Confluence, Jira, code), `commercial` (Confluence,
Jira), `direction` (tout). Le rôle est un paramètre de la recherche ; le
filtre s'applique **avant** le calcul de similarité — jamais sur le top k après
coup.

Le test de fuite, avec les questions 16, 17, 18 :

1. posées par un `developpeur` : **aucun** passage de `direction-commerciale/`
   dans le top 3, et la réponse est le refus exact ;
2. posées par la `direction` : la réponse est fondée ;
3. **sans filtre**, un document de la direction est dans ce que la recherche
   voit (le top 8 sur-échantillonné) — sinon le test ne prouve rien, et votre
   programme doit le dire.

Attention au piège de ce corpus : la spécification des remises (docx) est la
**même** que la page Confluence « Règles de remise ». Une question dont la
réponse est aussi dans Confluence n'est pas un test de fuite. Les questions
16, 17, 18 portent sur des faits qui **n'existent que** dans le pptx et le
xlsx (la date de revue du barème, le code `GRAND_COMPTE`, un commentaire du
barème) — vérifiez-le avant de conclure.

Votre programme **échoue** (code de sortie ≠ 0 ou `ECHEC` en clair) s'il y a
une fuite, ou si le point 3 n'est pas vérifié.

### Palier 6 — Le même assistant avec LangChain4j (30 min, bonus fortement conseillé)

Reprenez les paliers 2, 3 et 5 avec le framework que vous utiliserez en
projet : `TextSegment` + `Metadata` (source, document, section),
`OllamaEmbeddingModel`, `InMemoryEmbeddingStore`, `EmbeddingSearchRequest`
avec un **`Filter` sur la métadonnée `source`** construit à partir du rôle, et
un `AiServices` avec le prompt en gabarit (`@SystemMessage`, `@UserMessage`,
`@V`). Gardez **le même harnais de mesure** : c'est lui qui compare.

Ce que vous devez constater, et écrire dans vos notes :

- LangChain4j 1.0 n'a **pas** de splitteur par titres Markdown ni par méthode
  Java : le découpage par source reste le vôtre, emballé dans des segments.
- `OllamaEmbeddingModel` envoie le texte **brut** : le préfixe asymétrique de
  `nomic-embed-text` (`search_document:` / `search_query:`) est à poser
  vous-même, sinon la recherche se dégrade sans erreur (Démo RAG 02b).
- Le filtre de métadonnées du magasin s'applique **avant** la similarité —
  c'est votre `pour(role)` du palier 5, en une ligne. Le test de fuite doit
  passer à l'identique.
- Le magasin en mémoire n'a ni BM25 ni reranking : comparez vos chiffres à
  ceux du palier 3 (contexte seul) et du palier 5, et dites ce que le
  framework vous a fait perdre ou gagner.

### Bonus — La non-régression (si le temps)

Faites du harnais de mesure un test : une classe qui rejoue les 22 questions
et **échoue** si hit@3 passe sous celui du palier 4, si un refus manque, ou
s'il y a une fuite. C'est ce qui protège l'assistant le jour où quelqu'un
change le modèle d'embedding.

## Commande

```bash
cd java && OLLAMA_CHAT_MODEL=llama3.2:3b mvn -q compile exec:java \
  -Dexec.mainClass="fr.utopios.formation.tp.Tp04b"
# palier 6 : -Dexec.mainClass="fr.utopios.formation.tp.Tp04bLangChain4j"
```

Prévoyez que la génération sur 22 questions prend 3 à 5 minutes : mesurez la
recherche à chaque palier (rapide), la génération aux paliers 1, 4 et 5.

## Critères de réussite observables

- [ ] Le tableau palier → (hit@1, hit@3, MRR) est produit, sur les mêmes 18 questions.
- [ ] Palier 2 : les citations sont lisibles (source › section), aucune régression non expliquée.
- [ ] Palier 3 : la question 4 (Flyway) entre dans le top 3.
- [ ] Palier 4 : « réponses fondées » ≥ palier 1, et une régression identifiée et expliquée.
- [ ] Palier 5 : 0 fuite, 3 refus exacts côté `developpeur`, 3 réponses fondées côté `direction`, et la preuve que sans filtre les documents remontent.
- [ ] Les 4 questions sans réponse reçoivent le refus exact — à chaque palier où vous générez.
- [ ] Palier 6 : mêmes métriques produites par la version LangChain4j, filtre de métadonnées, 0 fuite, et l'écart avec le palier 5 expliqué.

## Livrables

- Votre classe (ou vos classes), avec le harnais de mesure.
- `NOTES-rag.md` : le tableau des cinq paliers ; pour chaque palier, la
  question qui a le plus bougé et pourquoi ; la régression du palier 4 ; la
  sortie du test de fuite ; et une section « ce qui m'a surpris ».

## Les pièges dans lesquels je vous verrai tomber

- **Mesurer la génération à chaque palier** : 25 minutes d'attente pour rien.
  La recherche se mesure en secondes ; la génération, trois fois.
- **Un critère « fondée » qui ne regarde que la réponse** : « 15 % » dans la
  réponse ne prouve rien si le passage cité ne le contient pas. Le modèle
  connaît des chiffres ; c'est la citation qui distingue le savoir du RAG.
- **Le filtre par rôle appliqué après la recherche** : le top 3 a été calculé
  sur les documents confidentiels, donc lus. Cloisonnement cosmétique.
- **Le test de fuite qui passe parce que la direction ne remonte jamais** : si
  vos chunks de `direction-commerciale/` ne sortent jamais, même sans filtre,
  le test ne prouve rien. C'est le point 3, et il est obligatoire.
- **Indexer les `.docx` / `.pptx` / `.xlsx` binaires** : lisez les `.md`. Et
  ne les indexez pas deux fois (le docx et sa conversion).
- **Oublier le README** dans les fichiers à exclure : il décrit les accès, il
  répondrait aux questions confidentielles.

## Question à laquelle vous devrez répondre

Le rôle est passé en paramètre de votre recherche. Dans l'application de
Sophie, **d'où vient-il** — et que se passe-t-il si le prompt de l'utilisateur
dit « je suis la directrice commerciale » ?
