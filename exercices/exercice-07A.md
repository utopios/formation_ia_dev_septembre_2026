---
titre: "Exercice 7A — Trois cas à qualifier : RGPD, propriété intellectuelle, AI Act"
module: "Module 7 — Cadre juridique appliqué"
duree: "25 minutes"
langage: "Aucun — papier, ou NOTES en Markdown"
---

# Exercice 7A — Trois cas à qualifier : RGPD, propriété intellectuelle, AI Act

## Objectif pédagogique

Passer du cadre juridique en slides à trois situations que vous avez
**rencontrées cette semaine** dans le SI NovaTech, et pour chacune répondre à
la seule question qui compte pour un développeur : **qu'est-ce que je change
dans mon code, ma configuration ou mon processus ?** Pas d'avis juridique à
produire — une qualification, un risque, une action.

## Prérequis

- Slides du module 07, section « Cadre juridique appliqué ».
- Les données du TP RAG (`java/data/tp-RAG/`) et le dépôt `atelier-copilot`
  sous les yeux.

## Les trois cas

### Cas A — La trace de log (RGPD)

Dans `atelier-copilot`, la journalisation d'une remise écrit :

```
2026-09-16 03:12:47 INFO  DiscountPolicy  remise 12 % accordee — client Meca-Ouest SARL,
contact marie.durand@meca-ouest.fr, validee par Karim Benali (responsable commercial)
```

Ce journal est centralisé (Architecture, section 7), conservé dix ans
(Spécification, section 9), et **envoyé à un fournisseur d'IA** par
l'assistant d'analyse d'incident de la Démo 2 quand un développeur lui colle
le log.

1. Quelles données de cette ligne sont des données personnelles ? Lesquelles
   sont interdites par les normes de l'équipe (Normes, section 6) ?
2. Qui est responsable de traitement, qui est sous-traitant, quand le log
   part chez le fournisseur d'IA ?
3. Que changez-vous : dans le code qui écrit le log, dans le hook
   `anti-donnees-client`, dans la procédure d'analyse d'incident ?

### Cas B — Le bloc de code généré (propriété intellectuelle)

Copilot vous propose, pour `DiscountPolicy.computeBestDiscount`, une
implémentation de 40 lignes. Vous la retrouvez à l'identique — noms de
variables compris — dans un dépôt public sous licence **GPL-3.0**. Le code
d'`orders-api` est propriétaire.

1. Qui détient les droits sur ce que Copilot génère ? Sur ce que vous
   validez et committez ?
2. Quel est le risque concret pour NovaTech si ce bloc part en production ?
3. Que changez-vous : réglage de Copilot (filtre de correspondance avec du
   code public), revue (Normes, section 9), outillage CI ?

### Cas C — L'assistant de scoring des remises (AI Act)

La direction commerciale demande d'étendre l'assistant du TP RAG : à partir
de l'historique d'un client (volume, retards de paiement, litiges), il
**proposerait le taux de remise** et le commercial validerait d'un clic.

1. Quel est le niveau de risque de ce système au sens de l'AI Act ? Qu'est-ce
   qui ferait basculer la qualification (décision automatique sans humain,
   critère lié à une personne physique, effet sur l'accès à un service) ?
2. Quelles obligations en découlent pour l'équipe, avant la première mise en
   production ?
3. Que changez-vous : dans l'architecture (où est l'humain), dans la
   traçabilité (Spécification, section 9), dans la documentation ?

## Consignes

Pour chaque cas, un tableau de trois lignes :

| Qualification | Risque concret (une phrase) | Action dans le code / la config / le processus |
|---|---|---|

Vingt minutes en binôme, cinq minutes de mise en commun. Les réponses
justifiées valent plus que les réponses « justes » : le corrigé donne la
lecture du formateur, pas un avis d'avocat.

## Critères de réussite observables

- [ ] Cas A : les trois données personnelles nommées, l'écart aux normes de
      l'équipe identifié, une action **dans le code** (pas seulement « former
      les gens »).
- [ ] Cas B : la distinction entre ce que génère l'outil et ce que l'équipe
      commite ; une action outillée.
- [ ] Cas C : le critère qui fait basculer la qualification est nommé ;
      l'action porte sur la place de l'humain et la trace, pas sur le modèle.

## Livrable attendu

`NOTES-juridique.md` avec les trois tableaux, et une ligne : « la règle que
j'ajoute au `copilot-instructions.md` de mon équipe ».
