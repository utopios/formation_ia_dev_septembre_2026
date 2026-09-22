# Appliquer un barème de remise par volume sur les commandes B2B

<!-- NOVA-3, reprise au gabarit de l'équipe. Le contenu est celui du ticket
     Jira ; seuls la forme des critères (une ligne par critère, au lieu du
     Gherkin) et le sourçage des chiffres ont été ajoutés — et l'arrondi,
     que le ticket affirme sans que la spec le dise, est passé en
     <À CONFIRMER> : c'est exactement ce que le skill demande. C'est la story de
     référence : ce que « bien rédigée » veut dire pour l'équipe Commandes. -->

## Contexte

Le barème de remise par volume est aujourd'hui appliqué manuellement par
l'administration des ventes. Cette story automatise son application pour les
clients de catégorie « Standard » et « Privilège ». Elle ne touche ni aux
remises promotionnelles ni aux frais de port.

## User story

En tant que client professionnel authentifié, je veux voir la remise par volume
appliquée automatiquement à mon panier, afin de connaître le montant final
avant de valider ma commande.

## Règles métier

- La remise se calcule sur le montant HT du panier, hors frais de port
  (« Règles de remise », section 2).
- Barème : montant HT inférieur à 1 000 EUR : 0 % ; de 1 000 EUR inclus à
  5 000 EUR exclu : 3 % ; de 5 000 EUR inclus à 20 000 EUR exclu : 6 % ; à
  partir de 20 000 EUR inclus : 9 % (« Règles de remise », section 2).
- Les clients de catégorie « Privilège » bénéficient de 2 points de pourcentage
  supplémentaires (« Règles de remise », section 2).
- La remise totale ne dépasse jamais 15 % (« Règles de remise », section 3).
- La remise par volume ne se cumule pas avec une remise contractuelle : la plus
  avantageuse des deux s'applique, jamais les deux (« Règles de remise »,
  sections 1 et 5).
- Les montants sont arrondis au centime, au plus proche, demi vers le haut
  (<À CONFIRMER : la règle d'arrondi n'est pas dans « Règles de remise » ; à
  faire confirmer par l'administration des ventes>).

## Critères d'acceptation

- Client « Standard », panier de 940,00 EUR HT : remise de 0,00 EUR, montant
  final 940,00 EUR (section 2).
- Client « Standard », panier de 5 000,00 EUR HT : taux appliqué 6 %, montant
  final 4 700,00 EUR (section 2).
- Client « Privilège », panier de 5 000,00 EUR HT : taux appliqué 8 %
  (section 2).
- Client « Privilège », panier de 25 000,00 EUR HT : taux appliqué 11 %, et le
  taux affiché ne dépasse jamais 15 % (section 3).
- Client « Standard » disposant d'une remise contractuelle de 10 %, panier de
  5 000,00 EUR HT : taux appliqué 10 %, la remise par volume n'est pas
  appliquée (sections 1 et 5).
- Client « Standard », panier de 0,00 EUR HT : remise de 0,00 EUR, aucune
  erreur levée (section 2).

## Hors périmètre

- Les remises promotionnelles par code campagne (story séparée, NOVA-S06).
- La remise sur les frais de port.

## Definition of done

- Couverture de tests unitaires du calculateur de remise supérieure à 90 %,
  bornes des deux côtés de chaque seuil.
- Endpoint `POST /api/v1/carts/{id}/recalculate` documenté dans l'OpenAPI.
- Journalisation de chaque remise appliquée : client, taux, palier,
  horodatage.
- Revue par un référent technique (« Normes et conventions », section 8).
