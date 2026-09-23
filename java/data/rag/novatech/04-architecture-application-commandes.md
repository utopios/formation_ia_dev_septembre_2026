# Architecture de l'application de gestion des commandes

- Espace cible : « NOVA — Spécifications »
- Propriétaire : équipe Architecture, NovaTech Industries
- Dernière mise à jour : 10 septembre 2026
- Statut : de référence

## 1. Vue d'ensemble

L'application `orders` est le cœur du tunnel de commande B2B. Elle est
déployée en trois instances derrière un répartiteur de charge, dans le centre
de données interne de NovaTech (pas de cloud public à ce jour).

```
Navigateur / tablette
        |
        v
  [ Reverse proxy nginx ]
        |
        v
  [ orders-front  (Angular 18, servi en statique) ]
        |  appels REST /api/v1
        v
  [ orders-api  (Spring Boot 3.3, 3 instances) ]
        |                    |                    |
        v                    v                    v
  [ PostgreSQL 16 ]   [ API Sage        ]   [ Vault ]
   (primaire +         (facturation,        (secrets)
    réplica lecture)     REST, externe)
        |
        v
  [ Spring Batch : export nocturne ]
        |
        v
  [ SFTP sftp-compta.novatech.lan ]
```

## 2. Composants

| Composant | Rôle | Technologie |
|---|---|---|
| `orders-front` | Interface client et commercial, responsive tablette | Angular 18 |
| `orders-api` | API REST, règles métier, calcul des remises | Spring Boot 3.3 / Java 21 |
| `orders-batch` | Export nocturne des remises vers la comptabilité | Spring Batch |
| `orders-db` | Persistance | PostgreSQL 16 |
| Sage | Facturation, hors périmètre de modification | API REST externe |
| Vault | Distribution des secrets | HashiCorp Vault |
| Control-M | Ordonnanceur d'entreprise, déclenche le batch par HTTP | Externe |

## 3. Découpage interne de `orders-api`

| Couche | Package | Responsabilité |
|---|---|---|
| Web | `com.novatech.orders.web` | Contrôleurs REST, DTO, mapping, gestion des erreurs HTTP |
| Service | `com.novatech.orders.service` | Orchestration, transactions, appels externes |
| Domaine | `com.novatech.orders.domain` | Règles métier pures : `DiscountPolicy`, `Cart`, `Customer`, `Order` |
| Infrastructure | `com.novatech.orders.infrastructure` | Accès base, client Sage, client Vault |

Classes structurantes du domaine :

- `DiscountPolicy` — applique le barème et le plafond. C'est ici que vit la
  règle des 15 %.
- `Cart` — agrégat panier, porte les lignes et le montant calculé.
- `Customer` — identité, catégorie, plafond de crédit, remise contractuelle.
- `Order` — commande validée, immuable une fois transmise à la facturation.

## 4. Modèle de données, tables principales

| Table | Contenu | Remarque |
|---|---|---|
| `customer` | Clients professionnels | Colonne `category` nullable (dette historique) |
| `cart` | Paniers en cours | Purge à 90 jours |
| `cart_line` | Lignes de panier | Prix unitaire figé à l'ajout |
| `orders` | Commandes validées | `order` étant réservé en SQL, la table est au pluriel par exception à la convention |
| `order_line` | Lignes de commande | Copie figée des lignes de panier |
| `order_discount` | Remise appliquée par commande | Ne porte pas encore l'origine de la remise : évolution attendue par NOVA-4 |
| `discount_validation` | Validations hiérarchiques | Demandeur, validateur, horodatage |
| `contract_discount` | Remises contractuelles grands comptes | Alimenté par reprise depuis le fichier commercial (NOVA-12) |

## 5. Intégrations externes

### API Sage (facturation)

- Protocole REST, authentification par clé d'API lue dans Vault.
- Appel synchrone à la validation de commande, délai d'expiration de 5 secondes.
- En cas d'indisponibilité : la commande est enregistrée en statut
  `PENDING_INVOICING` et rejouée par un mécanisme de reprise. Elle n'est jamais
  perdue ni rejetée à l'utilisateur.
- Idempotence assurée par l'envoi du numéro de commande comme clé
  d'idempotence.

### Export SFTP comptable

- Dépôt nocturne sur `sftp-compta.novatech.lan`, authentification par clé.
- Fichier CSV UTF-8, séparateur point-virgule, fin de ligne CRLF, en-tête
  obligatoire, nommé `remises_AAAAMMJJ.csv`.
- Fenêtre d'exécution : 20 minutes maximum. Volume nominal 400 à 1 200 lignes,
  pics à 4 000 en fin de trimestre.

## 6. Contraintes non fonctionnelles

| Exigence | Valeur |
|---|---|
| Temps de réponse du recalcul de panier | 300 ms au 95e centile |
| Disponibilité de l'API | 99,5 % en heures ouvrées |
| Interruption maximale lors d'une livraison | 15 minutes |
| Réversibilité des migrations de données | Obligatoire |
| Conservation du journal des remises | 10 ans |
| Sessions simultanées à supporter | 200 |

## 7. Observabilité

- Métriques Micrometer exposées vers Prometheus, tableaux de bord Grafana.
- Traces distribuées par OpenTelemetry sur les appels sortants vers Sage.
- Journaux structurés en JSON, centralisés, sans donnée personnelle.
- Alertes : absence de l'export nocturne, taux d'erreur 5xx supérieur à 1 %,
  latence du recalcul de panier au-delà du seuil, indisponibilité de Sage.

## 8. Dette technique connue

Ces points sont assumés et documentés, ils ne sont pas des découvertes.

1. `customer.category` est nullable : 412 fiches créées avant 2021 n'ont pas
   de catégorie. Origine du bug NOVA-5. Reprise de données en cours.
2. La table `orders` est au pluriel, par exception à la convention de nommage.
3. Le fichier Excel des remises contractuelles reste en circulation malgré son
   remplacement officiel (NOVA-12).
4. Le front ne gère pas encore la reconnexion automatique après expiration de
   session sur tablette : le commercial perd son panier en zone de mauvaise
   couverture réseau.
5. Aucune consolidation multi-sites dans le modèle de données : `customer` n'a
   pas de notion de groupe (voir NOVA-13).
