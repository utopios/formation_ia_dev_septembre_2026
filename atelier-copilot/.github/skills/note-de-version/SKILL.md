---
name: note-de-version
description: Rédige la note de version NovaTech à partir des tickets Jira livrés et du diff GitLab, selon le gabarit de l'équipe. À utiliser à la clôture d'un sprint ou avant une mise en production.
argument-hint: la version à documenter, par exemple 2.5
---

# Note de version NovaTech

Tu produis la note de version destinée aux **équipes commerciales et au support**,
pas aux développeurs. Ceux qui la lisent veulent savoir ce qui change pour leurs
clients, pas quelle classe a été refactorée.

## Quand utiliser ce skill

À la clôture d'un sprint, avant une mise en production, ou quand on te demande
« qu'est-ce qui change dans la 2.5 ? ».

Ne l'utilise pas pour un simple résumé de tickets : la note de version a un
gabarit imposé et un public précis.

## Procédure

### 1. Rassembler la matière

- Les tickets Jira du périmètre, avec leur statut et leur type.
- S'il existe une merge request ou un tag de version, le diff associé.
- La page Confluence « Règles de remise » **si** une règle métier a changé :
  la note doit citer la version de la spec en vigueur.

### 1 bis. Les pièces jointes — convertir avant de lire

Un ticket porte souvent une pièce jointe : le compte rendu du comité en
`.docx`, le barème en `.xlsx`, une spécification en `.pdf`.

**Ne transmets jamais un binaire au modèle.** Télécharge-le, convertis-le en
local, puis travaille sur le Markdown :

```bash
# 1. Telecharger les pieces jointes du ticket (outil MCP jira_download_attachments)
#    vers un dossier de travail, par exemple /tmp/pj-NOVA-42

# 2. Convertir, en local, sans reseau
./.github/skills/note-de-version/scripts/convertir-pieces-jointes.sh /tmp/pj-NOVA-42
```

Le script [convertir-pieces-jointes.sh](./scripts/convertir-pieces-jointes.sh)
affiche, pour chaque fichier, sa taille avant et après, et le ratio obtenu.
Mesuré sur les documents NovaTech : **10,8×** pour un `.docx`, **29,2×** pour
un `.pptx`, **9,3×** pour un `.xlsx`.

Trois raisons de passer par là, et la troisième est la moins connue :

1. **Le coût** — un `.pptx` est surtout de la mise en forme. Le Markdown ne
   garde que le contenu.
2. **La lisibilité** — le Markdown se diffe, se versionne, se relit.
3. **Ce qui ne suit pas** — un `.docx` est une archive ZIP qui transporte le
   nom de l'auteur, les mentions de confidentialité et parfois les
   commentaires supprimés. La conversion les laisse derrière.

**Codes de sortie du script** : `0` conversion faite ou rien à convertir,
`1` dossier introuvable, `2` markitdown absent. Dans ce dernier cas, le script
affiche la commande d'installation — ne l'invente pas.

**Si un `.pdf` produit un Markdown vide**, c'est un scan sans couche texte. Le
script le signale et supprime le fichier vide. Dis-le dans ta réponse au lieu
de conclure que la pièce jointe était vide : il faudrait un OCR.

### 2. Trier par ce que ça change pour le lecteur

Trois catégories, dans cet ordre. Une entrée qui n'entre dans aucune n'a rien à
faire dans la note.

| Catégorie | Ce qu'on y met |
|---|---|
| **Ce qui change pour vous** | Nouveau comportement visible : un écran, un calcul, un message |
| **Corrections** | Un dysfonctionnement constaté disparaît |
| **Sous le capot** | Une ligne maximum, et seulement si ça a un effet observable (performance, stabilité) |

### 3. Écrire dans la langue du lecteur

- **Pas de nom de classe, pas de nom de méthode, pas de numéro de ligne.**
- Un ticket dont le titre est technique se reformule : « Corriger le NPE dans
  `CustomerDao` » devient « Les fiches client créées avant 2021 ne provoquent
  plus d'erreur au calcul du panier ».
- Les montants et les taux se citent tels quels — ce sont eux qui intéressent.
- Une phrase par entrée. Deux au maximum.

### 4. Vérifier avant de rendre

- Chaque entrée renvoie à un **ticket réel** : pas d'entrée inventée pour
  étoffer.
- Un ticket qui n'est pas en statut livré **n'apparaît pas**, même s'il est
  presque fini. C'est l'erreur qui coûte le plus cher : annoncer une
  fonctionnalité absente.
- Si une règle métier change, la note cite la **version de la spécification**
  applicable.

## Les fichiers de ce skill

```
note-de-version/
  SKILL.md
  scripts/
    convertir-pieces-jointes.sh    binaire -> Markdown, en local
  references/
    gabarit.md                     la structure imposee
    exemple-2.4.md                 une note complete, bien formee
```

Les fichiers de `references/` ne sont chargés que si tu en as besoin ; le
script, lui, **s'exécute sans jamais entrer dans le contexte**. Tu lances la
commande et tu lis sa sortie — tu n'as pas à lire son code.

## Gabarit

Le gabarit officiel est dans [references/gabarit.md](./references/gabarit.md).
Respecte-le : l'équipe support l'a construit avec ses propres contraintes de
diffusion.

Un exemple complet et bien formé : [references/exemple-2.4.md](./references/exemple-2.4.md).

## Ce que tu ne fais pas

**Tu ne publies pas la note.** Tu la produis, un humain la relit et la diffuse.
Une note de version part aux clients : elle engage l'entreprise.

**Tu n'inventes pas de date de disponibilité.** Si elle n'est pas dans les
tickets, tu écris `<À CONFIRMER>`.

**Tu ne minimises pas une régression.** Si un ticket décrit un contournement
temporaire, il apparaît tel quel. Le support préfère une mauvaise nouvelle
écrite à une découverte au téléphone.
