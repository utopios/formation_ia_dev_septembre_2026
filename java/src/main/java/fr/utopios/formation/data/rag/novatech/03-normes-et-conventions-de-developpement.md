# Normes et conventions de développement — équipe Commandes

- Espace cible : « NOVA — Spécifications »
- Propriétaire : équipe Commandes, NovaTech Industries
- Dernière revue : 4 septembre 2026
- Statut : applicable, opposable en revue de code

Ce document est la référence que l'IA doit consulter avant de proposer du
code sur ce périmètre. Une proposition non conforme est refusée en revue,
quelle que soit sa qualité fonctionnelle.

## 1. Langages et versions

| Composant | Technologie | Version imposée |
|---|---|---|
| Back-end | Java | 21 (LTS) |
| Framework back | Spring Boot | 3.3.x |
| Front-end | Angular | 18 |
| Base de données | PostgreSQL | 16 |
| Migrations | Flyway | 10.x |
| Build | Maven | 3.9.x |
| Tests | JUnit 5, AssertJ, Testcontainers | — |

La montée de version d'une dépendance majeure passe par une décision
d'architecture écrite, jamais par une initiative de merge request.

## 2. Conventions de nommage

- Packages : `com.novatech.orders.<couche>`, couches autorisées : `web`,
  `service`, `domain`, `infrastructure`.
- Classes : `PascalCase`. Interfaces sans préfixe `I`.
- Méthodes et variables : `camelCase`, en anglais.
- Constantes : `UPPER_SNAKE_CASE`.
- Tables et colonnes PostgreSQL : `snake_case`, au singulier pour les tables
  (`order_discount`, pas `order_discounts`).
- Migrations Flyway : `V<AAAA>.<MM>.<JJ>.<n>__<description_snake_case>.sql`.
- Branches Git : `feature/<clé Jira>-<résumé-court>`, par exemple
  `feature/NOVA-4-export-remises`.
- Messages de commit : Conventional Commits, avec la clé Jira en portée,
  par exemple `feat(NOVA-4): exporter les remises vers le référentiel`.

## 3. Architecture en couches

Le sens des dépendances est strict et vérifié par ArchUnit :

```
web  ->  service  ->  domain
                  ->  infrastructure  ->  domain
```

- `domain` ne dépend d'aucun framework. Pas d'annotation Spring, pas de JPA
  dans les classes de domaine.
- Les règles métier vivent dans `domain`. Un calcul de remise dans un
  contrôleur est un motif de refus en revue.
- `web` n'expose jamais une entité de persistance : il expose des DTO.
- L'accès base passe par une interface déclarée dans `domain` et implémentée
  dans `infrastructure`.

## 4. Règles de codage

- Aucun type flottant pour un montant. `BigDecimal` exclusivement, avec
  `RoundingMode.HALF_UP` et une échelle de 2.
- Comparaison de `BigDecimal` par `compareTo`, jamais par `equals`.
- Pas de `null` renvoyé par une méthode publique : `Optional` ou exception
  métier explicite.
- Les exceptions métier héritent de `BusinessException` et portent un code
  stable, consommé par le front pour l'affichage.
- Pas de `@Autowired` sur un champ : injection par constructeur uniquement.
- Une méthode dépasse rarement 30 lignes ; au-delà, elle est découpée.
- Tout code mort est supprimé, jamais commenté.

## 5. Tests

- Couverture minimale exigée sur `domain` : 90 % de branches. Le build échoue
  en dessous.
- Un test par cas limite documenté dans la spécification. Les cas limites de
  la section 8 de la spécification fonctionnelle sont tous couverts.
- Nommage des tests : `should_<comportement>_when_<condition>` ou
  `<comportement>_when_<condition>`, en anglais.
- Les tests d'intégration utilisent Testcontainers avec PostgreSQL 16, jamais
  une base H2 : les différences de comportement SQL ont déjà causé deux
  incidents de production.
- Aucun `Thread.sleep` dans un test. Attente conditionnelle via Awaitility.
- Les jeux de données de test sont construits par des `builders`, pas par des
  fichiers SQL copiés.

## 6. Sécurité

- Aucun secret dans le code, dans `application.yml`, ni dans une variable
  d'environnement définie au build. Les secrets sont lus depuis HashiCorp
  Vault au démarrage.
- Aucune donnée personnelle ni raison sociale de client dans les traces
  applicatives. Les identifiants techniques sont autorisés.
- Toute requête SQL est paramétrée. La concaténation de chaînes pour
  construire du SQL est interdite.
- Les endpoints exposés sont authentifiés par défaut ; une exception
  d'authentification se déclare explicitement et se justifie en revue.
- Les dépendances sont analysées à chaque build. Une vulnérabilité critique
  bloque la livraison.

## 7. API REST

- Versionnement dans l'URL : `/api/v1/...`.
- Ressources au pluriel : `/api/v1/carts/{id}`.
- Codes de retour : 200 lecture, 201 création avec en-tête `Location`, 204
  suppression, 400 requête invalide, 404 ressource absente, 409 conflit
  métier, 422 règle métier violée.
- Les erreurs suivent le format `application/problem+json` (RFC 7807).
- Toute évolution d'API est décrite dans le contrat OpenAPI avant
  l'implémentation, pas après.

## 8. Revue de code

- Deux approbations minimum, dont une d'un référent technique de l'équipe.
- Une merge request dépasse rarement 400 lignes modifiées. Au-delà, elle est
  découpée.
- La merge request référence sa clé Jira et décrit comment la modification a
  été vérifiée.
- Le pipeline doit être vert avant toute approbation. Aucune approbation
  conditionnelle.

## 9. Usage de l'IA dans l'équipe

- Une production de l'IA (code, test, documentation) est relue ligne à ligne
  avant d'être proposée en revue. L'auteur de la merge request en est
  responsable, pas l'outil.
- Une interprétation IA d'une spécification n'est jamais implémentée sans
  vérification contre la spécification source.
- Aucun extrait de code propriétaire ni donnée client n'est transmis à un
  service d'IA non validé par la DSI.
