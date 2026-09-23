# Corpus du TP  — l'assistant documentaire de l'équipe Commandes

Tout ce qu'il faut pour le TP, et rien qui vienne d'ailleurs : ce sont les
documents réels du SI de simulation NovaTech, tels que les stagiaires les ont
vus dans Jira, Confluence, GitLab et le partage de la direction commerciale.

| Dossier | Contenu | Format d'origine | Qui a le droit de lire |
|---|---|---|---|
| `confluence/` | les 4 pages de l'espace NOVA : spécification fonctionnelle, règles de remise, normes de développement, architecture | Confluence, exporté en Markdown | tout le monde (`developpeur`, `commercial`, `direction`) |
| `jira/` | les 14 tickets du projet NOVA (epic, stories, bug), avec descriptions, critères, trace d'erreur | Jira, exporté en Markdown | tout le monde |
| `code/` | 5 classes d'`orders-api` : `DiscountPolicy`, `DiscountValidationService`, `Cart`, `OrderService`, `SageInvoicingClient` | GitLab, Java | `developpeur` |
| `direction-commerciale/` | la spécification des remises (**docx**), le compte rendu du comité tarifaire (**pptx**), le barème (**xlsx**) — chacun avec sa conversion Markdown par markitdown | Office | `direction` **seulement** — le compte rendu contient des points non tranchés et une date de revue du barème que les commerciaux ne doivent pas voir |

Les fichiers Office sont fournis avec leur conversion `.md` : le TP indexe les
`.md`, mais la Démo 2c a montré comment on les obtient, et les tableaux du docx
sont exactement ceux que le chunking doit ne pas couper.

## Le jeu d'évaluation — `questions.json`

22 questions, dans le format que le code lit :

```json
{"id": 1, "question": "Qui doit valider une remise de 13 % ?",
 "source": "confluence", "section": "Seuils de délégation",
 "reponse": "directeur commercial", "acces": "tous"}
```

- `source` : le dossier où se trouve la réponse (`confluence`, `jira`, `code`,
  `direction-commerciale`), ou plusieurs séparés par `|` quand l'information est
  à deux endroits ; `null` pour les questions **sans réponse dans le corpus**
  (19 à 22).
- `section` : un fragment du titre de la section attendue (ou du nom de la
  classe pour le code), alternatives séparées par `|`. C'est ce qui sert à
  mesurer la **recherche** (le bon chunk est-il dans le top k ?).
- `reponse` : un fragment que la réponse générée doit contenir, alternatives
  séparées par `|`, comparé sans accents ni majuscules. C'est ce qui sert à
  mesurer la **génération** (la réponse est-elle fondée ?).
- `acces` : `tous`, ou `direction` pour les trois questions dont la réponse
  n'existe que dans `direction-commerciale/`. Ce sont les questions du **test
  de fuite** : posées par un `developpeur`, elles doivent recevoir le refus,
  et aucun chunk confidentiel ne doit apparaître dans les passages.

Répartition : 15 questions ouvertes à tous (10 Confluence, 2 Jira, 3 code ou
code + Confluence), 3 confidentielles, 4 sans réponse.
