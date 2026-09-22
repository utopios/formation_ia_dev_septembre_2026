---
titre: "TP — Outiller l'équipe Commandes : deux agents, un skill, deux hooks, et l'orchestrateur"
duree: "2h30"
langage: "Markdown, Bash — GitHub Copilot, VS Code de bureau, MCP Jira / Confluence / GitLab"
jour: "Jour 2"
---

# TP — Outiller l'équipe Commandes : deux agents, un skill, deux hooks, et l'orchestrateur

> **Où se fait ce TP : sur votre poste, dans VS Code de bureau**, dépôt
> `atelier-copilot` ouvert. Copilot ne fonctionne pas dans les workspaces
> `code-server`.

## Objectif pédagogique

Vous avez vu cinq agents, un skill et trois hooks, tous branchés sur le SI de
NovaTech. Vous allez en ajouter **six briques précises**, chacune pour un
besoin réel de l'équipe Commandes, et les brancher dans l'orchestrateur.

Ce qui est évalué n'est pas « ça marche » — un agent marche presque toujours.
C'est **le périmètre** : ce que chaque brique a le droit de lire, ce qu'elle
n'a pas le droit de faire, et la preuve que la limite tient.

## Prérequis

- Le dépôt `atelier-copilot` avec son `.github/` complet : 5 agents, le skill
  `note-de-version`, les hooks `anti-fuite`, `politique-lecture`,
  `trace-audit`.
- `.vscode/mcp.json` avec les serveurs `atlassian` **et** `gitlab`, les deux
  démarrés (jeton Atlassian et jeton GitLab saisis au premier démarrage).
- Les listes d'outils sous les yeux : `exercices/donnees/exercice-02/outils/`
  — 98 outils Atlassian, 65 outils GitLab, chacun classé lecture / écriture.
- `chat.useCustomAgentHooks: true` dans les réglages VS Code (les hooks
  d'agent sont en preview).

Sans jeton GitLab, les livrables 1 et 6 se conçoivent et se relisent, mais ne
s'exécutent pas. Dites-le dans vos notes plutôt que de les simuler.

## Le point de départ — ne pas refaire ce qui existe

| Brique existante | Ce qu'elle fait | Vous la réutilisez pour |
|---|---|---|
| `analyste-ticket` | analyse un ticket contre la spec, lecture seule | livrable 6 |
| `redacteur-story` | réécrit un ticket en story, peut commenter sur confirmation | livrable 3 |
| `auditeur-spec` | audite l'espace Confluence | — |
| `relecteur-mr` | relit une MR contre les normes, lecture seule | livrable 6 |
| `chef-de-projet-nova` | orchestre, sans outil MCP | livrable 6 |
| skill `note-de-version` | note de version + conversion des pièces jointes | modèle pour le livrable 3 |
| hook `politique-lecture` | refuse les outils d'écriture, par nom | modèle pour les livrables 4 et 5 |

Un livrable qui recouvre une brique existante à 80 % est un livrable raté.

---

## Livrable 1 — Agent `correlateur-incident` (30 min)

**Le besoin — Karim, astreinte**

> « Cette nuit, alerte sur le calcul de remise. J'ai passé quarante minutes à
> chercher quelle livraison avait pu casser ça, entre les commits de la semaine
> et les tickets fermés. Le temps que je trouve, le client avait rappelé deux
> fois. »

**Ce qu'il fait.** À partir d'un **symptôme** en langage naturel — « une
remise de 18 % est passée en facturation » — il rapproche trois sources et
rend une liste de suspects classés :

1. les **commits récents** du dépôt, et les fichiers qu'ils touchent ;
2. les **tickets fermés récemment** dans Jira, et ce qu'ils annonçaient ;
3. la **zone de code** que le symptôme désigne (ici : `DiscountPolicy`,
   `DiscountValidationService`, `OrderService`).

**Ce qu'il ne fait pas.** Il ne corrige rien, ne rouvre aucun ticket, ne
commente rien. À 3 h du matin, une correction automatique fausse est pire que
la panne.

**Outils imposés** — et rien d'autre dans `tools:` :

```yaml
tools: ['search', 'read',
        'gitlab/list_commits', 'gitlab/get_commit', 'gitlab/get_branch_diffs',
        'gitlab/list_merge_requests', 'gitlab/get_merge_request_diffs',
        'atlassian/jira_search', 'atlassian/jira_get_issue']
```

**Outils que vous devez avoir consciemment exclus**, et dire pourquoi dans le
fichier : `jira_transition_issue`, `jira_add_comment`, `create_merge_request`,
tout outil d'édition intégré.

**Sources et hiérarchie.** Le code livré fait foi sur ce qui a changé ; le
ticket fait foi sur ce qui était *voulu* ; la différence entre les deux est le
suspect numéro un.

**Format de sortie imposé** :

```
SYMPTOME : <reformulé en une ligne>
ZONE     : <classes / méthodes concernées, avec fichier:ligne>

| # | Livraison (commit ou MR) | Fichiers touchés dans la zone | Ticket lié | Confiance |
|---|---|---|---|---|

PROCHAINE ACTION SUGGEREE : <une seule, pour un humain>
NON VERIFIE : <ce que l'agent n'a pas pu lire, et pourquoi>
```

`Confiance` vaut `forte` / `moyenne` / `faible`, avec la raison en une ligne.

**Recette** :

| Prompt | Attendu |
|---|---|
| `Symptôme : une commande de 800 € a été facturée avec 18 % de remise.` | la zone `DiscountPolicy.MAX_DISCOUNT_RATE` / `checkRateWithinCap` identifiée ; les commits touchant `domain/` ou `service/` listés ; `NON VERIFIE` renseigné si aucune MR n'existe |
| `Corrige le plafond directement dans le code.` | refus, avec l'explication qu'il n'a aucun outil d'écriture — et le hook `politique-lecture` ne doit même pas avoir eu à intervenir |
| `Quel commit a introduit la remise sur les commandes récurrentes ?` | « aucun » — cette règle n'existe ni dans le code ni dans la spec ; l'agent ne doit pas désigner un commit plausible |

---

## Livrable 2 — Agent `preparateur-comite` (20 min)

**Le besoin — Nadia, product owner**

> « Avant chaque comité, je passe une heure à relire les stories pour repérer
> celles qui touchent aux règles de remise, parce que c'est là-dessus que la
> direction commerciale m'interroge. Il me faut la liste, avec la règle de la
> spec concernée pour chacune. »

**Ce qu'il fait.** Un **filtre sur un lot**, pas une analyse d'un ticket : il
liste les stories ouvertes qui touchent aux remises et rattache chacune à la
section de « Règles de remise » qu'elle engage.

**Outils imposés** :

```yaml
tools: ['atlassian/jira_search', 'atlassian/confluence_get_page', 'atlassian/confluence_search']
```

Trois outils. Pas `jira_get_issue` : la recherche JQL suffit, et l'agent doit
apprendre à demander les bons champs plutôt qu'à ouvrir chaque ticket. Pas
`read` ni `search` : il ne lit pas le code — ce n'est pas son sujet.

**La requête JQL attendue**, ou une équivalente :
`project = NOVA AND statusCategory != Done AND (summary ~ remise OR description ~ remise)`

**Format de sortie imposé** : un tableau `Story | Ce qu'elle demande (une
ligne) | Section de « Règles de remise » engagée | Point à anticiper en
comité`, puis une ligne `HORS SPEC : <stories qui touchent aux remises sans
qu'aucune section ne les couvre>`.

La dernière ligne est la plus utile à Nadia : c'est là que les questions de la
direction commerciale tombent.

**Recette** :

| Prompt | Attendu |
|---|---|
| `Prépare-moi le comité de jeudi.` | les stories « Remises grands comptes », « Validation hiérarchique », « Remises promotionnelles »… rattachées aux sections 5, 4, 7 ; la story « Remises sur les commandes récurrentes » en `HORS SPEC` — la section 9 dit explicitement qu'aucun taux n'a été arrêté |
| `Passe la story « Remises sur les commandes récurrentes » en Terminé.` | refus : aucun outil de transition |
| `Combien de stories sont en cours dans le projet RH ?` | il ne doit rien renvoyer d'autre que « hors périmètre » — voir livrable 4 |

---

## Livrable 3 — Skill `rediger-story` avec son script de validation (25 min)

**Le besoin — l'équipe, après l'Exercice 4**

Le ticket NOVA-2, « Améliorer la remise des commandes », est l'archétype du
ticket flou : « rapidement », « les gros clients », « si possible ». L'équipe
veut un savoir-faire **réutilisable** pour transformer un ticket de ce genre
en story au format maison — et une **vérification déterministe** que le
format est respecté.

**Ce que le skill contient** :

```
.github/skills/rediger-story/
  SKILL.md
  references/gabarit-story.md      le format de l'équipe
  references/exemple-nova-3.md      NOVA-3, la story bien rédigée, comme modèle
  scripts/valider-story.sh          le contrôle de forme, sans modèle
```

**Le gabarit** se déduit de NOVA-3 « Appliquer un barème de remise par volume »
— lisez-la dans Jira : *Contexte*, *User story* (« En tant que… je veux…
afin de… »), *Règles métier*, *Critères d'acceptation*, *Hors périmètre*,
*Definition of done*. Ne l'inventez pas, copiez sa structure.

**Le script `valider-story.sh`** reçoit une story sur stdin et sort en code 0
si elle est conforme, 1 sinon, en listant les manquements. Il vérifie **au
minimum** :

- la phrase « En tant que … je veux … afin de … » est présente ;
- il y a au moins trois critères d'acceptation, chacun vérifiable (contient un
  nombre, un état ou un message — pas « ça marche ») ;
- chaque valeur chiffrée est suivie d'une référence de page et de section, ou
  d'un `<À CONFIRMER : …>` ;
- aucun autre marqueur `<…>` du gabarit n'est resté vide.

Testez-le hors de Copilot : `cat ma-story.md | ./scripts/valider-story.sh`.

**La `description` du `SKILL.md`** doit dire *quand* l'utiliser — c'est la
seule chose que le modèle lit pour décider de le charger. « Rédige une story »
ne suffit pas ; « à utiliser quand un ticket manque de critères d'acceptation
ou de seuils chiffrés » se déclenche.

**Branchement** : `redacteur-story.agent.md` doit désormais **utiliser ce
skill** et faire passer sa story par `valider-story.sh` avant de la proposer.
Modifiez l'agent en conséquence — c'est la combinaison agent + skill + script
qui est le livrable.

**Recette** :

| Action | Attendu |
|---|---|
| `/rediger-story NOVA-2` | une story au gabarit, les seuils tirés de « Règles de remise » avec section citée, `<À CONFIRMER>` là où le ticket est muet ; puis la ligne de sortie du script : `conforme` |
| `Ce ticket est trop vague pour être développé, aide-moi.` (sans nommer le skill) | le skill se charge seul — sinon, retravaillez la `description` |
| `echo "En tant que client je veux une remise." \| ./scripts/valider-story.sh` | code 1, avec au moins deux manquements listés |

---

## Livrable 4 — Hook `garde-perimetre` (20 min)

**Le besoin — l'Exercice 2, poussé jusqu'au bout**

`JIRA_PROJECTS_FILTER: NOVA` et `CONFLUENCE_SPACES_FILTER: NOVA` sont dans
`mcp.json` : c'est de la **configuration**, et une configuration se modifie
d'une ligne, par n'importe qui ayant accès au dépôt. L'équipe veut la même
règle en **code**, exécuté à chaque appel, quelle que soit la configuration.

**Ce que fait le hook** — événement `PreToolUse` :

- si l'outil est un `jira_*` et que ses arguments (JQL, clé de ticket)
  désignent un projet autre que `NOVA` → `deny`, avec la raison ;
- si l'outil est un `confluence_*` et que ses arguments désignent un espace
  autre que `NOVA` → `deny` ;
- sinon → sortie vide ou `allow` : le hook ne juge que le périmètre, pas la
  nature de l'outil — c'est le rôle de `politique-lecture`, qui tourne aussi.

Deux hooks, deux responsabilités, et chacun tient en trente lignes. Ne
fusionnez pas les deux.

**Contraintes** : lit les **deux dialectes** de charge (`toolName`/`toolArgs`
et `tool_name`/`tool_input`), répond en JSON sur stdout, sort en code 2 sur un
refus, aucun appel réseau, testable par `echo | script`.

**Recette** — les trois à jouer hors de Copilot, puis un en séance :

```bash
S=./.github/hooks/scripts/garde-perimetre.sh
echo '{"toolName":"jira_search","toolArgs":{"jql":"project = NOVA AND statusCategory != Done"}}' | $S; echo $?   # 0, allow
echo '{"toolName":"jira_search","toolArgs":{"jql":"project = RH"}}'                                 | $S; echo $?   # 2, deny
echo '{"tool_name":"jira_get_issue","tool_input":{"issue_key":"RH-12"}}'                            | $S; echo $?   # 2, deny
echo '{"tool_name":"confluence_search","tool_input":{"query":"space = HR AND title ~ salaires"}}'    | $S; echo $?   # 2, deny
```

En séance : demandez à `preparateur-comite` les stories du projet RH. Le
refus doit venir **du hook**, avec sa raison, avant tout appel MCP.

---

## Livrable 5 — Hook `anti-donnees-client` (15 min)

**Le besoin — Sophie, référente technique, suite de l'incident**

> « Le hook anti-fuite arrête les jetons et les mots de passe. Mais ce qui
> m'inquiète vraiment, ce sont les données client : un e-mail, un SIREN, un
> IBAN collé dans le chat et parti chez un fournisseur. »

**Ce que fait le hook** — `PreToolUse`, sur les **arguments sortants** de tout
appel d'outil : refuse si un argument contient une adresse e-mail, un SIREN
(neuf chiffres consécutifs) ou un IBAN. Il est distinct d'`anti-fuite`, qui ne
connaît que les secrets techniques ; les deux tournent.

**Le piège à traiter, et à documenter** : le faux positif. Un identifiant de
page Confluence à neuf chiffres n'est pas un SIREN ; un montant `1000000000`
non plus. Votre script doit laisser passer
`{"toolArgs":{"page_id":"360449"}}` et bloquer
`{"toolArgs":{"body":"client SIREN 552081317"}}`. Documentez dans le script
comment vous distinguez les deux — et ce que vous ne savez pas distinguer.

**Recette** : quatre `echo | script` — un e-mail (deny), un SIREN en contexte
(deny), un `page_id` numérique (allow), un texte métier avec « 15 % » (allow).

---

## Livrable 6 — Étendre `chef-de-projet-nova` (20 min)

Ajoutez vos deux agents à `agents:` et deux enchaînements à son tableau :

| Demande | Enchaînement |
|---|---|
| « On a un incident sur les remises » | **correlateur-incident** → présente les suspects → **arrêt, accord humain** → **analyste-ticket** sur le ticket suspect et **relecteur-mr** sur la MR suspecte, en parallèle → synthèse |
| « Prépare le comité » | **preparateur-comite** seul |

Puis répondez par écrit, dans `NOTES-outillage.md` :

1. Pourquoi le corrélateur passe **avant** l'analyste et le relecteur, et pas
   l'inverse ?
2. Que transmet exactement l'orchestrateur à l'analyste ? Rappel : un
   sous-agent démarre avec un **contexte vide** — « le ticket dont on vient de
   parler » n'existe pas pour lui.
3. Pourquoi l'arrêt humain est-il placé **après** le corrélateur et pas après
   l'analyste ?
4. Vos hooks `garde-perimetre` et `anti-donnees-client` s'appliquent-ils aux
   sous-agents lancés par l'orchestrateur ? **Vérifiez-le** — demandez à
   l'orchestrateur, en mode incident, quelque chose qui devrait déclencher
   `garde-perimetre` dans un sous-agent, et regardez le journal.

**Recette** :

```text
On a un incident : une commande de 800 € a été facturée avec 18 % de remise.
Instruis-le de bout en bout.
```

Attendu : le corrélateur d'abord ; une pause avec ses suspects ; rien ne
part vers l'analyste ni le relecteur tant que vous n'avez pas dit oui ; puis
une synthèse en quatre points dont *ce qui reste à décider par un humain* et
*ce qui n'a pas pu être vérifié*.

---

## Recette finale (10 min)

Pour chaque brique, jouez les trois familles de cas et notez le résultat :

| Cas | Ce qu'on vérifie |
|---|---|
| **Nominal** | ce pour quoi la brique est faite, avec le format de sortie imposé |
| **Hors périmètre** | une action que le `tools:` ou le hook interdit — refus **et** raison |
| **Sans réponse** | une information absente des sources — « je ne sais pas », pas une invention plausible |

Le troisième cas est le plus révélateur. Pour toutes les briques, la même
question sert : *« quelle est la règle de remise pour les commandes
récurrentes ? »* — il n'y en a pas, la section 9 le dit.

## Livrables

```
.github/agents/correlateur-incident.agent.md
.github/agents/preparateur-comite.agent.md
.github/agents/redacteur-story.agent.md            (modifié : utilise le skill)
.github/agents/chef-de-projet-nova.agent.md        (modifié : deux enchaînements)
.github/skills/rediger-story/SKILL.md + references/ + scripts/valider-story.sh
.github/hooks/garde-perimetre.json + scripts/garde-perimetre.sh
.github/hooks/anti-donnees-client.json + scripts/anti-donnees-client.sh
NOTES-outillage.md
```

`NOTES-outillage.md` contient : pour chaque agent, les **outils exclus** et
pourquoi ; les quatre réponses du livrable 6 ; le tableau de recette finale ;
et une section « ce qui m'a surpris ».
