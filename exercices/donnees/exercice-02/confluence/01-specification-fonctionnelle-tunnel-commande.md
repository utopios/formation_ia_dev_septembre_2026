# Spécification fonctionnelle — Tunnel de commande

- Espace cible : « NOVA — Spécifications »
- Version : 2.4 — applicable au 1er septembre 2026
- Propriétaire : Direction commerciale NovaTech Industries
- Statut : approuvée
- Épopée Jira associée : NOVA-1 « Refonte du tunnel de commande »

## 1. Objet et périmètre

Ce document décrit le comportement attendu du tunnel de commande de la
plateforme B2B NovaTech Industries : constitution du panier, calcul des
montants, application des remises, validation et transmission à la
facturation.

Sont hors périmètre : la gestion du catalogue produits, la logistique
d'expédition, et le module de facturation Sage (consommé via son API REST).

## 2. Acteurs

| Acteur | Rôle dans le tunnel |
|---|---|
| Client professionnel | Constitue son panier et valide sa commande |
| Commercial itinérant | Constitue un panier pour le compte d'un client |
| Administration des ventes | Reprend les commandes en anomalie, saisit les gestes commerciaux |
| Responsable commercial | Valide les remises au-delà du seuil de délégation |
| Contrôle de gestion | Consomme l'export nocturne des remises |

## 3. Catégories de clients

Tout client professionnel porte une catégorie, qui conditionne son barème.

| Catégorie | Code | Critère d'attribution |
|---|---|---|
| Standard | `STANDARD` | Catégorie par défaut à la création du compte |
| Privilège | `PRIVILEGE` | Chiffre d'affaires HT des 12 derniers mois supérieur ou égal à 60 000 EUR |
| Grand compte | `KEY_ACCOUNT` | Contrat cadre signé, attribution manuelle par la direction commerciale |

Règle de robustesse : un compte dont la catégorie est absente ou vide en base
est traité comme `STANDARD`. Aucune commande ne doit être bloquée pour ce
motif. 412 fiches créées avant 2021 sont dans ce cas et font l'objet d'une
reprise de données.

La recatégorisation est recalculée le 1er de chaque mois. Un client ne perd
jamais sa catégorie en cours de mois.

## 4. Constitution du panier

- Un panier appartient à un client et à un seul.
- Quantité minimale de commande : 1 unité. Quantité maximale par ligne : 9 999.
- Un panier expire après 30 jours d'inactivité et est purgé après 90 jours.
- Les prix unitaires sont figés au moment de l'ajout au panier pendant
  72 heures. Passé ce délai, le panier est recalculé au tarif courant et le
  client en est informé.
- Montant minimum de commande : 150 EUR HT. En dessous, la validation est
  refusée avec le message « Montant minimum de commande : 150 EUR HT ».

## 5. Calcul des montants

Ordre de calcul, impératif et non négociable :

1. Somme des lignes : quantité multipliée par prix unitaire HT.
2. Application de la remise (voir page « Règles de remise »).
3. Ajout des frais de port.
4. Application de la TVA sur le total HT remisé, frais de port inclus.

Précisions :

- La remise porte exclusivement sur le montant HT des articles, jamais sur
  les frais de port ni sur la TVA.
- Arrondi au centime, arrondi au plus proche, demi vers le haut
  (`HALF_UP`), appliqué une seule fois en fin de calcul de remise.
- Taux de TVA : 20 % en France métropolitaine. Les clients intracommunautaires
  disposant d'un numéro de TVA valide sont exonérés (autoliquidation).
- Devise unique : euro. Le multidevise n'est pas au programme de l'exercice.

## 6. Frais de port

| Montant HT après remise | Frais de port HT |
|---|---|
| Inférieur à 500 EUR | 24,90 EUR |
| De 500 EUR inclus à 2 000 EUR exclu | 12,50 EUR |
| À partir de 2 000 EUR inclus | Offerts |

Les produits marqués « hors gabarit » sont facturés 89 EUR de port
supplémentaires, quel que soit le montant de la commande.

## 7. Validation de la commande

Contrôles bloquants à la validation, dans cet ordre :

1. Le panier contient au moins une ligne.
2. Le montant HT après remise atteint 150 EUR.
3. Chaque référence est encore active au catalogue.
4. Le client n'a pas d'encours impayé supérieur à 5 000 EUR.
5. L'encours total du client, commande en cours comprise, ne dépasse pas son
   plafond de crédit.
6. Si la remise appliquée dépasse le seuil de délégation, la commande part en
   validation hiérarchique et n'est pas transmise à la facturation avant accord.

Un contrôle en échec renvoie un message explicite désignant la ligne ou le
motif concerné. Le panier n'est jamais vidé par un échec de validation.

## 8. Cas limites recensés

Ces cas sont des exigences, pas des suggestions.

- Panier vide : remise de 0,00 EUR, aucune erreur levée.
- Montant négatif ou nul sur une ligne : rejet en erreur de validation.
- Client sans catégorie : traité comme `STANDARD` (voir section 3).
- Référence désactivée au catalogue pendant que le panier est ouvert : la ligne
  est signalée et doit être retirée avant validation.
- Client intracommunautaire sans numéro de TVA renseigné : la TVA française
  s'applique.
- Commande annulée le jour même : elle apparaît dans l'export des remises avec
  un indicateur d'annulation, elle n'est jamais omise.
- Commande modifiée après validation : une ligne corrective est produite,
  l'historique n'est jamais réécrit.
- Deux paniers simultanés pour le même client depuis deux sessions : le dernier
  enregistrement gagne, une alerte est journalisée.

## 9. Traçabilité

Chaque remise appliquée est journalisée avec : numéro de commande, code
client, catégorie, montant HT avant remise, taux appliqué, palier ou origine,
identifiant du validateur en cas de validation manuelle, horodatage.

Ce journal est la source de l'export nocturne vers le référentiel comptable
(ticket Jira NOVA-4). Il est conservé 10 ans pour l'audit.

Les traces applicatives ne doivent journaliser aucune raison sociale de
client.
