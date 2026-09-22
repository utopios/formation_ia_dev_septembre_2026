---
name: rediger-story
description: Transforme un ticket flou ou une section de spécification en user story au format de l'équipe Commandes, avec critères d'acceptation vérifiables et seuils sourcés. À utiliser quand un ticket manque de critères d'acceptation, de seuils chiffrés, ou contient des « si possible » et « rapidement ».
argument-hint: la clé du ticket, par exemple NOVA-2
---

# Rédiger une story au format NovaTech

Tu produis une story que **le développeur peut prendre lundi** : chaque seuil
est chiffré et sourcé, chaque critère d'acceptation se teste, rien n'est laissé
à l'interprétation.

## Quand utiliser ce skill

Quand un ticket contient « rapidement », « les gros clients », « si
possible », « voir avec le service commercial » — ou quand on te demande de
préparer une story à partir d'une section de la spécification.

Ne l'utilise pas pour résumer un ticket déjà bien rédigé.

## Procédure

### 1. Lire les sources, dans cet ordre

1. Le ticket, tel quel — c'est l'**intention**.
2. La page Confluence « Règles de remise » — c'est la **vérité métier**. Quand
   le ticket la contredit, la spécification gagne, et tu le signales dans la
   story.
3. Le code, si un seuil ou un comportement existant est en jeu.

### 2. Écrire au gabarit

Le gabarit est dans [references/gabarit-story.md](./references/gabarit-story.md).
Il est déduit de la story NOVA-3, « Appliquer un barème de remise par volume »,
que l'équipe considère comme bien rédigée : lis-la dans
[references/exemple-nova-3.md](./references/exemple-nova-3.md) avant d'écrire.

Six sections, toujours dans cet ordre : Contexte, User story, Règles métier,
Critères d'acceptation, Hors périmètre, Definition of done.

### 3. Chiffrer et sourcer

- Chaque valeur chiffrée — un taux, un seuil, un délai — est suivie de sa
  source : `(« Règles de remise », section 3)`.
- Ce que le ticket ne dit pas et que la spécification ne dit pas non plus
  devient `<À CONFIRMER : la question à poser>`. **Tu n'inventes jamais un
  chiffre plausible** : un `<À CONFIRMER>` visible vaut mieux qu'un 10 %
  sorti de nulle part qui passera en développement.

### 4. Vérifier avant de rendre

Passe ta story par le script de validation, **avant** de la proposer :

```bash
./.github/skills/rediger-story/scripts/valider-story.sh < ma-story.md
```

Il sort en `0` si la forme est conforme, en `1` avec la liste des manquements
sinon. Corrige et repasse. Une story que le script refuse ne se propose pas.

Le script vérifie la **forme** — présence de la phrase « En tant que… je
veux… afin de… », au moins trois critères vérifiables, chaque chiffre sourcé
ou marqué `<À CONFIRMER>`, aucun marqueur de gabarit resté vide. Il ne juge
pas le **fond** : c'est toi, puis un humain.

## Les fichiers de ce skill

```
rediger-story/
  SKILL.md
  references/gabarit-story.md      le format, six sections
  references/exemple-nova-3.md     NOVA-3 telle qu'elle est dans Jira
  scripts/valider-story.sh         le contrôle de forme, déterministe
```

Le script s'exécute sans entrer dans le contexte : tu lances la commande, tu
lis sa sortie.

## Ce que tu ne fais pas

**Tu ne publies pas la story.** Tu la proposes ; l'agent qui t'utilise
demande confirmation avant tout commentaire dans Jira.

**Tu ne fusionnes pas deux tickets** en une story, même s'ils se ressemblent.
Une story, un ticket.

**Tu ne tranches pas un « sauf cas particulier ».** Tu le transformes en
`<À CONFIRMER : quels cas particuliers, et qui les valide ?>`.
