# Projet NOVA — les tickets, tels que le connecteur les expose

Photographie du projet Jira de simulation. Ce que vous lisez ici est
exactement ce qu'un assistant branche par MCP peut lire — et rien de
plus, grace au filtre `JIRA_PROJECTS_FILTER: NOVA`.

> Les cles Jira (`NOVA-1`, `NOVA-2`...) sont attribuees a la creation du
> projet et varient selon l'ordre d'import. Designez les tickets par leur
> titre.

| # | Type | Titre | Rattache a |
|---|---|---|---|
| 1 | Epic | Refonte du tunnel de commande | — |
| 2 | Story | Ameliorer la remise des commandes | Refonte du tunnel de commande |
| 3 | Story | Appliquer un bareme de remise par volume sur les commandes B2B | Refonte du tunnel de commande |
| 4 | Story | Exporter l'historique des remises accordees vers le referentiel comptable | Refonte du tunnel de commande |
| 5 | Bug | Erreur 500 lors du recalcul du panier quand le client n'a pas de categorie | Refonte du tunnel de commande |
| 6 | Story | [S01] Gestion des remises grands comptes | Refonte du tunnel de commande |
| 7 | Story | [S02] Remises sur les commandes recurrentes | Refonte du tunnel de commande |
| 8 | Story | [S03] Remise de bienvenue pour les nouveaux clients professionnels | Refonte du tunnel de commande |
| 9 | Story | [S04] Validation hierarchique des remises exceptionnelles | Refonte du tunnel de commande |
| 10 | Story | [S05] Affichage du detail de la remise dans le panier | Refonte du tunnel de commande |
| 11 | Story | [S06] Remises promotionnelles par code campagne | Refonte du tunnel de commande |
| 12 | Story | [S07] Reprise des remises negociees depuis le fichier commercial | Refonte du tunnel de commande |
| 13 | Story | [S08] Remises sur les commandes multi-sites d'un meme groupe | Refonte du tunnel de commande |
| 14 | Story | [S09] Suppression progressive des remises accordees a titre historique | Refonte du tunnel de commande |

---

## 1. Refonte du tunnel de commande

- **Type** : Epic
- **Etiquettes** : simulation-formation, nova-tunnel

NovaTech Industries commercialise des composants industriels aupres de 340 clients professionnels. Le tunnel de commande actuel date de 2019 et concentre l'essentiel des reclamations du service client.

Objectifs de la refonte pour l'exercice en cours :

- Reduire le taux d'abandon au panier, aujourd'hui de 31 %.
- Industrialiser le calcul des remises, aujourd'hui repris a la main par l'administration des ventes dans 18 % des commandes.
- Rendre le parcours utilisable sur tablette par les commerciaux itinerants.
- Tracer chaque decision de remise pour l'audit annuel.

Perimetre technique : application Java 21 / Spring Boot 3.3 (back), Angular 18 (front), PostgreSQL 16. Le module de facturation Sage n'est pas dans le perimetre : il est consomme via son API REST existante.

Contraintes : aucune interruption de service superieure a 15 minutes ; la migration des donnees de remise doit etre reversible.

---

## 2. Ameliorer la remise des commandes

- **Type** : Story
- **Etiquettes** : simulation-formation, demo-formateur, a-analyser
- **Rattache a** : Refonte du tunnel de commande

Les commerciaux se plaignent que la gestion des remises est trop lente et pas assez souple. Il faudrait ameliorer ca rapidement, c'est assez urgent pour les gros clients.

En gros, quand un client important passe une commande d'un montant eleve, il devrait avoir une remise plus interessante que ce qu'on fait aujourd'hui. Aujourd'hui le commercial doit appeler l'administration des ventes, c'est lourd. On aimerait que ce soit automatique quand c'est possible.

Il faut aussi que la remise soit visible pour le client, si possible avant qu'il valide sa commande. Et prevoir le cas ou le client a deja une remise negociee, dans ce cas on ne cumule pas (enfin, sauf cas particulier, voir avec le service commercial).

Attention a ne pas casser l'export vers la comptabilite.

Merci de traiter en priorite, le directeur commercial en a parle en comite.

---

## 3. Appliquer un bareme de remise par volume sur les commandes B2B

- **Type** : Story
- **Etiquettes** : simulation-formation, demo-formateur, bien-redigee
- **Rattache a** : Refonte du tunnel de commande

### Contexte

Le bareme de remise par volume est aujourd'hui applique manuellement par l'administration des ventes. Cette story automatise son application pour les clients de categorie « Standard » et « Privilege ».

### User story

En tant que client professionnel authentifie, je veux voir la remise par volume appliquee automatiquement a mon panier, afin de connaitre le montant final avant de valider ma commande.

### Regles metier

- La remise se calcule sur le montant HT du panier, hors frais de port.
- Bareme : montant HT < 1 000 EUR : 0 % ; de 1 000 EUR inclus a 5 000 EUR exclu : 3 % ; de 5 000 EUR inclus a 20 000 EUR exclu : 6 % ; a partir de 20 000 EUR inclus : 9 %.
- Les clients de categorie « Privilege » beneficient de 2 points de pourcentage supplementaires.
- La remise totale ne depasse jamais 15 %.
- La remise par volume ne se cumule pas avec une remise contractuelle : si le client dispose d'une remise contractuelle, c'est la plus avantageuse des deux qui s'applique, jamais les deux.
- Les montants sont arrondis au centime, arrondi au plus proche, demi vers le haut.

### Criteres d'acceptation

```gherkin
Fonctionnalite: Remise par volume sur le panier B2B

  Scenario: Panier sous le premier palier
    Etant donne un client de categorie "Standard"
    Et un panier de 940.00 EUR HT
    Quand le panier est recalcule
    Alors la remise appliquee est de 0.00 EUR
    Et le montant HT final est de 940.00 EUR

  Scenario: Palier intermediaire pour un client Standard
    Etant donne un client de categorie "Standard"
    Et un panier de 5 000.00 EUR HT
    Quand le panier est recalcule
    Alors le taux de remise applique est de 6 %
    Et le montant HT final est de 4 700.00 EUR

  Scenario: Bonus Privilege
    Etant donne un client de categorie "Privilege"
    Et un panier de 5 000.00 EUR HT
    Quand le panier est recalcule
    Alors le taux de remise applique est de 8 %

  Scenario: Plafond global
    Etant donne un client de categorie "Privilege"
    Et un panier de 25 000.00 EUR HT
    Quand le panier est recalcule
    Alors le taux de remise applique est de 11 %
    Et le taux applique ne depasse pas 15 %

  Scenario: Non cumul avec une remise contractuelle plus avantageuse
    Etant donne un client de categorie "Standard" disposant d'une remise contractuelle de 10 %
    Et un panier de 5 000.00 EUR HT
    Quand le panier est recalcule
    Alors le taux de remise applique est de 10 %
    Et la remise par volume n'est pas appliquee

  Scenario: Panier vide
    Etant donne un client de categorie "Standard"
    Et un panier de 0.00 EUR HT
    Quand le panier est recalcule
    Alors la remise appliquee est de 0.00 EUR
    Et aucune erreur n'est levee
```

### Hors perimetre

- Les remises promotionnelles par code campagne (story separee).
- La remise sur les frais de port.

### Definition of done

- Couverture de tests unitaires du calculateur de remise superieure a 90 %.
- Endpoint `POST /api/v1/carts/{id}/recalculate` documente dans l'OpenAPI.
- Journalisation de chaque remise appliquee (client, taux, palier, horodatage).

---

## 4. Exporter l'historique des remises accordees vers le referentiel comptable

- **Type** : Story
- **Etiquettes** : simulation-formation, demo-formateur, plan-implementation
- **Rattache a** : Refonte du tunnel de commande

### Contexte

L'audit annuel exige de justifier chaque remise accordee. Aujourd'hui l'information est reconstituee a la main depuis les bons de commande PDF, ce qui a coute 6 jours-homme au dernier exercice.

### User story

En tant que controleur de gestion, je veux recevoir chaque nuit un export des remises accordees la veille, afin de rapprocher les montants factures du bareme en vigueur et de justifier les ecarts en audit.

### Besoin fonctionnel detaille

- Une ligne par commande ayant beneficie d'une remise, quelle qu'en soit l'origine (bareme volume, remise contractuelle, geste commercial saisi manuellement).
- Colonnes attendues : numero de commande, code client, raison sociale, categorie client, montant HT avant remise, taux applique, montant de la remise, origine de la remise, identifiant de l'utilisateur ayant valide si validation manuelle, horodatage de la commande.
- Les commandes annulees dans la journee apparaissent avec un indicateur d'annulation plutot que d'etre omises.
- Une commande modifiee apres coup genere une ligne corrective, l'historique n'est jamais reecrit.
- Le controleur de gestion doit pouvoir rejouer l'export d'une date passee sans risque de doublon cote destinataire.

### Contraintes techniques imposees

- Application Java 21 / Spring Boot 3.3, base PostgreSQL 16. La table `order_discount` existe deja mais ne porte pas l'origine de la remise : une evolution de schema est necessaire, via Flyway (les migrations manuelles sont interdites).
- L'ordonnancement se fait par Spring Batch, declenche par le scheduler d'entreprise (Control-M) via un appel HTTP authentifie, pas par un cron applicatif.
- Le fichier produit est un CSV UTF-8, separateur point-virgule, fin de ligne CRLF, en-tete obligatoire, nomme `remises_AAAAMMJJ.csv`.
- Depot du fichier sur un serveur SFTP interne (`sftp-compta.novatech.lan`), authentification par cle, cle lue depuis Vault et jamais depuis la configuration applicative.
- Volume attendu : 400 a 1 200 lignes par nuit, avec des pics a 4 000 en fin de trimestre. La fenetre d'execution est de 20 minutes maximum.
- Toute execution doit etre idempotente : rejouer la meme date ne doit pas produire deux fichiers differents ni deux depots.
- Les donnees exportees contiennent des donnees clients : la trace applicative ne doit journaliser aucune raison sociale.
- Observabilite : metrique Micrometer du nombre de lignes exportees et de la duree, alerte si l'export d'une nuit est absent.

### Points a trancher pendant la conception

- Reprise sur incident : reprise a la ligne ou relance complete de la nuit ?
- Conservation des fichiers produits cote application, ou depot unique et sans copie locale ?

---

## 5. Erreur 500 lors du recalcul du panier quand le client n'a pas de categorie

- **Type** : Bug
- **Etiquettes** : simulation-formation, demo-formateur, commentaire-resolution
- **Rattache a** : Refonte du tunnel de commande

### Description

Depuis la mise en production du 12 septembre, le recalcul du panier renvoie une erreur 500 pour certains clients. Le service client remonte 23 cas en 4 jours. Les clients concernes sont tous des comptes crees avant 2021 dont la fiche n'a jamais ete migree : leur champ `categorie` est nul en base.

### Reproduction

1. Se connecter avec le compte de test `client.legacy@novatech.example` (categorie nulle en base).
2. Ajouter au panier la reference `NT-4471`, quantite 30.
3. Appeler `POST /api/v1/carts/8842/recalculate`.
4. L'API renvoie HTTP 500 et le panier reste dans son etat precedent.

### Comportement attendu

Un client sans categorie doit etre traite comme un client de categorie « Standard » et le recalcul doit aboutir.

### Trace d'erreur

```
2026-09-16 03:12:47.881 ERROR 1 --- [http-nio-8080-exec-7] c.n.o.web.CartController
        : Unhandled exception on POST /api/v1/carts/8842/recalculate
java.lang.NullPointerException: Cannot invoke "java.lang.String.toUpperCase()"
        because the return value of
        "com.novatech.orders.domain.Customer.getCategory()" is null
    at com.novatech.orders.domain.DiscountPolicy.resolveTier(DiscountPolicy.java:64)
    at com.novatech.orders.domain.DiscountPolicy.computeVolumeDiscount(DiscountPolicy.java:41)
    at com.novatech.orders.service.CartService.recalculate(CartService.java:118)
    at com.novatech.orders.web.CartController.recalculate(CartController.java:73)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:255)
    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
    ... 58 common frames omitted
```

### Impact

Bloquant pour les clients concernes : ils ne peuvent plus passer commande en ligne et basculent sur le telephone. 23 commandes reprises manuellement.

### Code livre pour corriger (extrait du diff de la merge request !124)

```java
// DiscountPolicy.java
-    private Tier resolveTier(Customer customer, BigDecimal amountExclVat) {
-        String category = customer.getCategory().toUpperCase();
+    private static final String DEFAULT_CATEGORY = "STANDARD";
+
+    private Tier resolveTier(Customer customer, BigDecimal amountExclVat) {
+        String category = Optional.ofNullable(customer.getCategory())
+                .filter(c -> !c.isBlank())
+                .map(String::toUpperCase)
+                .orElse(DEFAULT_CATEGORY);
         return TIERS.stream()
                 .filter(t -> t.matches(category, amountExclVat))
                 .findFirst()
                 .orElseThrow(() -> new UnknownTierException(category, amountExclVat));
     }
```

```java
// DiscountPolicyTest.java — tests ajoutes
+    @Test
+    void treats_null_category_as_standard() {
+        Customer c = new Customer("C-1904", null);
+        assertThat(policy.computeVolumeDiscount(c, new BigDecimal("5000.00")))
+                .isEqualByComparingTo(new BigDecimal("300.00"));
+    }
+
+    @Test
+    void treats_blank_category_as_standard() {
+        Customer c = new Customer("C-1905", "   ");
+        assertThat(policy.computeVolumeDiscount(c, new BigDecimal("5000.00")))
+                .isEqualByComparingTo(new BigDecimal("300.00"));
+    }
```

Une migration Flyway `V2026.09.17.1__backfill_customer_category.sql` renseigne par ailleurs la categorie « STANDARD » pour les 412 fiches clients concernees.

---

## 6. [S01] Gestion des remises grands comptes

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s01
- **Rattache a** : Refonte du tunnel de commande

Nos grands comptes negocient leurs conditions chaque annee mais l'application ne sait pas les gerer correctement. Il faut revoir ca pour la prochaine campagne commerciale.

Quand un grand compte commande, il doit beneficier de ses conditions negociees. Le probleme c'est que ces conditions sont dans un tableur maintenu par le service commercial et que personne ne sait vraiment laquelle est a jour. Il faudrait que l'application soit la reference.

On veut aussi pouvoir accorder un geste commercial exceptionnel au-dela des conditions negociees, dans des limites raisonnables et avec l'accord de la hierarchie si le montant est important.

Les commerciaux doivent voir tout ca dans leur interface, de maniere claire.

A faire avant la fin du trimestre.

---

## 7. [S02] Remises sur les commandes recurrentes

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s02
- **Rattache a** : Refonte du tunnel de commande

Certains clients repassent les memes commandes tous les mois et on aimerait les recompenser. C'est un levier de fidelisation que la concurrence utilise deja.

L'idee est que si un client commande regulierement, il obtienne une remise supplementaire. Reste a definir ce qu'on entend par regulierement : le service marketing parle de plusieurs commandes sur une periode, sans avoir tranche.

Il faudrait aussi que le client soit informe qu'il a droit a cette remise, et pourquoi, sinon ca ne sert a rien en termes de fidelisation.

Attention, cette remise ne doit pas se cumuler n'importe comment avec les autres, sinon on va perdre de la marge sur les gros volumes.

Prevoir un suivi pour que le controle de gestion puisse mesurer le cout de l'operation.

---

## 8. [S03] Remise de bienvenue pour les nouveaux clients professionnels

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s03
- **Rattache a** : Refonte du tunnel de commande

On veut faciliter la premiere commande des nouveaux clients pros, le taux de conversion a l'inscription est faible.

Un nouveau client devrait avoir une remise sur sa premiere commande, plus interessante que le bareme standard, pour l'inciter a tester. La duree de validite reste a definir avec le marketing, quelques semaines apparemment.

Il faut eviter les abus : certains creent plusieurs comptes pour la meme entreprise. Le service commercial a signale le cas plusieurs fois.

La remise doit apparaitre dans le tunnel de commande de maniere visible, mais sans donner l'impression que nos prix habituels sont trop hauts.

Voir aussi si on peut l'appliquer aux clients inactifs depuis longtemps qui reviennent, ca a ete evoque en reunion.

---

## 9. [S04] Validation hierarchique des remises exceptionnelles

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s04
- **Rattache a** : Refonte du tunnel de commande

Aujourd'hui un commercial peut accorder n'importe quelle remise sans controle et on a eu de mauvaises surprises au dernier bilan.

Il faut mettre en place une validation : au-dela d'un certain niveau de remise, la demande part en validation aupres du responsable. Le seuil doit etre parametrable parce qu'il changera.

Le commercial doit savoir ou en est sa demande. Le validateur doit etre prevenu rapidement, parce que le client attend son devis.

Si personne ne valide, il faut prevoir quelque chose, on ne peut pas laisser un client sans reponse indefiniment.

Il faudra pouvoir retrouver qui a valide quoi, pour l'audit.

Le directeur commercial veut aussi pouvoir passer outre la validation dans les cas urgents.

---

## 10. [S05] Affichage du detail de la remise dans le panier

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s05
- **Rattache a** : Refonte du tunnel de commande

Les clients ne comprennent pas comment leur remise est calculee et appellent le service client pour demander des explications. Ca represente une part non negligeable des appels.

Il faudrait afficher dans le panier le detail de ce qui est applique, de facon comprehensible pour un acheteur qui n'est pas dans nos regles metier.

Le service juridique a demande que ce soit precis, notamment sur la TVA et les frais de port, mais on n'a pas encore le detail de leur demande.

Ca doit rester lisible sur tablette, les commerciaux itinerants s'en servent chez le client.

Si le client a plusieurs remises possibles, montrer pourquoi c'est celle-la qui s'applique et pas une autre, parce que c'est souvent le motif de l'appel.

Pas de regression sur les performances d'affichage du panier, c'est deja limite.

---

## 11. [S06] Remises promotionnelles par code campagne

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s06
- **Rattache a** : Refonte du tunnel de commande

Le marketing veut lancer des campagnes avec des codes promo, comme on en voit partout, et pouvoir les gerer sans passer par l'informatique.

Un code doit pouvoir etre limite dans le temps, en nombre d'utilisations et a certains produits ou certains clients. Le marketing veut de la souplesse mais n'a pas ecrit les combinaisons attendues.

Le client saisit son code dans le panier et voit tout de suite le resultat. S'il se trompe, il doit comprendre pourquoi ca ne marche pas.

Question a trancher : est-ce qu'un code promo se cumule avec la remise par volume ? Les avis divergent entre le marketing et le controle de gestion.

Il faut pouvoir desactiver une campagne en urgence si elle derape.

Prevoir un bilan par campagne pour le marketing.

---

## 12. [S07] Reprise des remises negociees depuis le fichier commercial

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s07
- **Rattache a** : Refonte du tunnel de commande

Les conditions negociees des clients sont aujourd'hui dans un fichier Excel partage, avec des incoherences. Il faut les faire entrer dans l'application.

Le fichier contient les taux par client mais aussi des lignes obsoletes, des doublons et des clients qui n'existent plus dans notre base. Le service commercial dit que le fichier est fiable, l'administration des ventes dit le contraire.

Il faudrait importer ce qui est exploitable et signaler le reste pour arbitrage humain. Le volume est de plusieurs centaines de lignes.

Apres l'import, le fichier Excel ne doit plus etre la reference, mais le service commercial souhaite continuer a le consulter pendant un temps.

Ne rien perdre : si une condition est ecrasee par erreur, on doit pouvoir revenir en arriere.

A caler avec la refonte du tunnel pour ne pas faire l'import deux fois.

---

## 13. [S08] Remises sur les commandes multi-sites d'un meme groupe

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s08
- **Rattache a** : Refonte du tunnel de commande

Certains de nos clients sont des groupes avec plusieurs sites qui commandent separement. Aujourd'hui chaque site est traite comme un client independant et le groupe estime perdre de la remise.

Le groupe veut que le volume soit consolide pour le calcul de la remise, meme si les commandes et les livraisons restent par site. La facturation, elle, reste par site d'apres la comptabilite, mais ce point n'est pas confirme.

Il faut savoir quels sites appartiennent a quel groupe, information que nous n'avons pas de maniere fiable aujourd'hui.

Sur quelle periode on consolide le volume, ca reste a definir avec le service commercial.

Un site ne doit pas voir les commandes des autres sites, le groupe y tient.

Voir l'impact sur les exports comptables avant de se lancer.

---

## 14. [S09] Suppression progressive des remises accordees a titre historique

- **Type** : Story
- **Etiquettes** : simulation-formation, stagiaire, s09
- **Rattache a** : Refonte du tunnel de commande

On traine des remises accordees il y a des annees, parfois sans trace de qui les a decidees, et elles pesent sur la marge. La direction veut les faire disparaitre, mais sans perdre de clients.

Il faut identifier ces remises, puis les reduire progressivement plutot que d'un coup. Le rythme n'est pas arrete : le controle de gestion veut aller vite, le commerce veut de la douceur.

Les clients concernes doivent etre prevenus avant que ca change, avec un delai correct. Qui les previent et comment, ce n'est pas tranche.

Certains clients sont strategiques et doivent etre exclus de l'operation, la liste sera fournie plus tard.

Il faut pouvoir mesurer l'effet sur le chiffre d'affaires de ces clients pour arreter l'operation si ca tourne mal.

Point de vigilance : pas de modification retroactive des commandes deja passees.
