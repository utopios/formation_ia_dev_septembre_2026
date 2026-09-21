# Conventions du projet orders-api

Tunnel de commande B2B de NovaTech Industries : constitution du panier, calcul
des remises, validation hiérarchique, transmission à la facturation.

Java 21, Maven. Architecture en couches, dépendances à sens unique :

```
web -> service -> domain <- infrastructure
```

Le `domain` ne dépend d'aucune autre couche. Une classe de `domain` qui importe
`web`, `service` ou `infrastructure` est une erreur, jamais une simplification.

## Règles non négociables

- **Tous les montants sont des `BigDecimal`, jamais des `double` ni des
  `float`.** Un montant facturé au client ne tolère pas d'erreur d'arrondi :
  `0.1 + 0.2` ne vaut pas `0.3` en virgule flottante, et la différence finit
  sur une facture. Les comparaisons se font avec `compareTo`, jamais avec `==`
  ni `equals`.
- **Arrondi explicite** : `setScale(2, RoundingMode.HALF_UP)` sur tout montant
  monétaire. Jamais d'arrondi implicite.
- **Aucun secret en dur.** Clés d'API, mots de passe et jetons viennent de la
  configuration, jamais du code source.
- **Les requêtes SQL sont paramétrées** (`?`), jamais construites par
  concaténation de valeurs.

## Règles métier structurantes

### Le plafond de remise

**Le taux de remise total ne dépasse en aucun cas 15 %.**

Cette règle vit dans `DiscountPolicy`, constante `MAX_DISCOUNT_RATE`, et la
vérification se fait par `checkRateWithinCap`. **Aucune autre couche ne
recalcule ce plafond** : toute remise, d'où qu'elle vienne, passe par là.

Un dépassement est **rejeté** par une `DiscountCapExceededException`, il n'est
jamais simplement signalé ni silencieusement ramené à 15 %.

> **Attention à l'homonymie.** `DiscountValidationService` porte une constante
> `SALES_DIRECTOR_THRESHOLD` qui vaut également 15. Ce n'est **pas** le même
> concept : c'est le seuil au-delà duquel plus aucune délégation n'est possible.
> Les deux constantes sont indépendantes et ne se référencent pas. Une
> modification de l'une n'implique pas l'autre — et aucun test ne le détecterait.

### Le barème par volume

Calculé sur le montant HT des articles, frais de port exclus. Paliers définis
dans `DiscountPolicy.TIERS`, ordonnés du plus élevé au plus bas :

| Montant HT | Taux |
|---|---|
| ≥ 20 000 EUR | 9 % |
| ≥ 5 000 EUR | 6 % |
| ≥ 1 000 EUR | 3 % |
| < 1 000 EUR | 0 % |

Bonus de catégorie ajouté au taux du palier, défini dans `CustomerCategory` :
`STANDARD` 0 point, `PRIVILEGE` 2 points, `GRAND_COMPTE` 4 points.

### Non-cumul

Une commande ne bénéficie que d'**une seule** remise : la plus avantageuse.
Le taux contractuel remplace le barème par volume, il ne s'y ajoute pas.

### Validation hiérarchique

Définie dans `DiscountValidationService` : jusqu'à 8 % automatique, jusqu'à
12 % le responsable commercial, jusqu'à 15 % le directeur commercial. Au-delà,
interdit.

Un geste commercial exige un motif d'au moins 20 caractères et laisse une trace
nominative.

## Format des réponses

- **En français**, y compris les commentaires et les messages d'exception.
- **Le code d'abord**, l'explication ensuite et en quelques lignes.
- **Citer les fichiers concernés** par leur chemin quand tu proposes une
  modification qui en touche plusieurs.
- **Ne jamais inventer** une règle métier absente du code. Si une information
  manque pour répondre, le dire plutôt que de combler.
- Quand une modification touche au calcul de remise, **signaler explicitement**
  si le plafond de 15 % reste garanti.
