---
name: 'Conventions de test'
applyTo: '**/*Test.java'
---

Ces conventions ne valent que pour les fichiers de test. Elles sont déduites
des tests existants du dépôt, pas inventées : `DiscountPolicyTest` en est la
référence.

## Bibliothèque d'assertions

**AssertJ exclusivement** : `assertThat(...)`, `assertThatThrownBy(...)`.
Jamais `assertEquals`, jamais `assertTrue` sur une comparaison.

Sur un `BigDecimal`, utiliser `isEqualByComparingTo` et non `isEqualTo` :
`new BigDecimal("3")` et `new BigDecimal("3.0")` ne sont pas `equals`, mais
sont égaux au sens monétaire.

## Nommage

`verbe_condition_resultatAttendu`, en minuscules séparées par des `_` :

```java
applies_no_discount_when_amount_below_first_tier()
applies_three_percent_when_amount_reaches_one_thousand()
rejects_rate_above_cap()
```

Le nom décrit un **comportement métier**, pas une méthode technique. On ne
nomme pas un test `testComputeVolumeDiscount`.

## Structure

Grouper par règle métier avec `@Nested` et `@DisplayName` :

```java
@Nested
@DisplayName("Bareme par volume")
class VolumeTiers {
    ...
}
```

**Un test par comportement**, pas un test par méthode. `DiscountPolicy` compte
18 tests pour quelques méthodes publiques : chaque palier, chaque borne et
chaque cas d'erreur a le sien.

## Couverture attendue

Pour toute règle comportant des seuils, tester **les bornes des deux côtés** :

- la valeur qui atteint le seuil (`1000` → 3 %) ;
- la valeur juste en dessous (`999.99` → 0 %) ;
- la valeur juste sous le seuil suivant (`4999.99` → 3 %).

C'est là que les erreurs d'inclusion se logent, et c'est exactement ce que les
tests existants vérifient.

Tout cas limite mentionné dans la spécification métier doit avoir son test.

## Interdictions

- Pas de `Thread.sleep` : utiliser Awaitility si une attente est nécessaire.
- Pas de dépendance entre tests : chacun doit passer seul, dans n'importe quel
  ordre.
- Pas de valeur magique non expliquée : si `15` apparaît, dire de quel 15 il
  s'agit — le plafond de `DiscountPolicy` ou le seuil de délégation de
  `DiscountValidationService`.
