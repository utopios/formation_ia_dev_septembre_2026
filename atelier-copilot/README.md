# orders-api 

Coeur du tunnel de commande B2B de NovaTech Industries : constitution du
panier, calcul des remises, validation hierarchique, transmission a la
facturation.

Ce depot est la copie de travail utilisee pour les ateliers outilles de
l'equipe Commandes. Il porte le code du domaine tel qu'il tourne en
production et une version allegee des couches techniques.

## Ou ouvrir ce depot

**Sur votre poste, dans VS Code de bureau** — pas dans le workspace VS Code du
serveur de formation.

La raison est simple : GitHub Copilot est une extension proprietaire de
Microsoft qui ne fonctionne pas dans `code-server` (le VS Code accessible par
navigateur). L'extension s'y connecte mais ne trouve aucun modele.

La repartition de la formation est donc la suivante :

| Activite | Ou |
|---|---|
| Ateliers GitHub Copilot, ce depot | **votre poste**, VS Code de bureau |
| Connecteurs MCP Jira / Confluence | **votre poste** (les serveurs Atlassian sont dans le cloud) |
| Demos Java, exercices, TP | **workspace du serveur** (Ollama et pgvector y sont installes) |

Ce depot est concu pour cela : il ne demande ni Ollama, ni base de donnees, ni
conteneur. Java 21 et Maven suffisent, et le build prend une seconde.

## Recuperer le depot

```bash
cd atelier-copilot
mvn clean test
```

Java 21 et Maven 3.9 suffisent. Le build ne demande aucune base de donnees,
aucun conteneur et aucun acces reseau autre que le depot Maven.

## Connecteurs MCP

Le fichier `.vscode/mcp.json` est fourni, pret a l'emploi. Au premier
demarrage, VS Code demande l'adresse e-mail et le jeton d'API Atlassian, puis
les range dans le trousseau du systeme : **le fichier ne contient aucun
secret**, il se partage et se versionne sans risque.

Prerequis : l'outil `uv` doit etre installe (il s'installe dans votre dossier
personnel, sans droits administrateur).

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh          # macOS, Linux
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"   # Windows
```

Verifiez avant la formation que la commande `uvx mcp-atlassian --help` repond :
elle telecharge le serveur au premier appel, et un proxy d'entreprise restrictif
peut bloquer cet acces.

**Les outils MCP n'apparaissent qu'en mode Agent**, pas en mode Ask.

## Ce qui differe de la production

L'application reelle est un Spring Boot 3.3 adosse a PostgreSQL 16, appelant
Sage et Vault. Dans cette copie :

- les couches `web` et `infrastructure` sont des POJO sans annotation : memes
  noms de classes, memes signatures, memes responsabilites, mais pas de
  conteneur d'injection ni de pilote JDBC ;
- `SqlExecutor` journalise les requetes au lieu de les executer ;
- `SageInvoicingClient` n'emet pas d'appel HTTP.

La couche `domain` en revanche est celle de production : elle ne depend
d'aucun framework, conformement a la norme d'architecture de l'equipe.

## Organisation

| Couche | Package | Contenu |
|---|---|---|
| Web | `com.novatech.orders.web` | `OrderController`, DTO, erreurs RFC 7807 |
| Service | `com.novatech.orders.service` | `OrderService`, `DiscountValidationService` |
| Domaine | `com.novatech.orders.domain` | `DiscountPolicy`, `Cart`, `Customer`, `Order` |
| Infrastructure | `com.novatech.orders.infrastructure` | `CustomerDao`, `OrderDao`, client Sage |

Le sens des dependances est strict : `web` vers `service` vers `domain`,
`infrastructure` vers `domain`.

## Documents de reference

Les regles implementees ici viennent de l'espace Confluence « NOVA —
Specifications » :

- « Specification fonctionnelle — Tunnel de commande » 2.4
- « Regles de remise » 2.4
- « Normes et conventions de developpement — equipe Commandes »
- « Architecture de l'application de gestion des commandes »

En cas d'ecart entre le code et ces pages, ce sont les pages qui font foi.

## Dette technique connue

- `customer.category` est nullable : 412 fiches creees avant 2021 n'ont pas de
  categorie. Une reprise de donnees est en cours.
- La table `orders` est au pluriel, par exception a la convention : `order`
  est un mot reserve SQL.
- La table `order_discount` ne porte pas encore l'origine de la remise, ce qui
  bloque l'export nocturne attendu par le controle de gestion.
