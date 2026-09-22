---
titre: "Exercice 2 — Cartographier ce qu'un assistant voit vraiment, et décider ce qu'on lui délègue"
module: "Module 2 — Analyse multi-formats & GitHub Copilot"
duree: "30 minutes"
langage: "Aucun — analyse et décision"
jour: "Jour 2"
---

# Exercice 2 — Cartographier ce qu'un assistant voit vraiment, et décider ce qu'on lui délègue

## Objectif pédagogique

Brancher un assistant sur les outils de l'entreprise élargit sa surface
d'exposition de façon qui n'est **jamais visible dans l'interface**. Le chat
affiche une réponse ; il n'affiche pas les trois cents pages qu'il a pu lire
pour la produire.

Vous allez produire deux livrables réutilisables tels quels en réunion
d'équipe :

1. une **fiche de cartographie** — pour chaque connecteur, ce qui devient
   atteignable et à quel degré de sensibilité ;
2. une **règle d'arbitrage** — par type d'action, où l'on reste dans la boucle
   et où l'on délègue.

**Aucun code.** C'est un exercice d'analyse et de décision, et c'est
volontaire : ces deux documents se rédigent une fois, se relisent à plusieurs,
et servent ensuite tous les jours.

## Prérequis

- Le bloc « Copilot en profondeur, MCP et sécurité » vu la veille.
- Le dépôt `atelier-copilot` ouvert, dont le `.vscode/mcp.json` déclare le
  connecteur Atlassian.
- Le dossier `exercices/donnees/exercice-02/` : la photographie de ce que
  les connecteurs exposent.

Vous n'avez besoin d'aucun modèle, d'aucun conteneur, d'aucune base — **ni
d'accès à Jira ou Confluence**. Le jeton Atlassian sera distribué cet
après-midi pour la Démo 6b ; pour cartographier, la photographie suffit, et
c'est voulu : on cartographie un périmètre avant d'y brancher quoi que ce soit.

## Le dossier de données

```
exercices/donnees/exercice-02/
  README.md
  jira/tickets.md          les 14 tickets du projet NOVA, tels que le connecteur les expose
  confluence/*.md          les 4 pages de l'espace « NOVA — Spécifications »
  gitlab/apercu.md         ce que le connecteur GitLab voit — le code, vous l'avez déjà
  outils/atlassian.md      les 98 outils que le serveur Atlassian expose, lecture / écriture
  outils/gitlab.md         les 65 outils que le serveur GitLab expose, lecture / écriture
```

C'est **exactement** ce qui est dans Jira et Confluence : le dossier est
généré depuis les mêmes sources. Ce que vous y lisez est ce qu'un assistant
branché lirait.

## Le périmètre : le SI de NovaTech Industries

Vous cartographiez le système d'information de la formation. Il est réel — vous
y accéderez demain avec la Démo 6b — et il est représentatif : un Jira, un
Confluence, un GitLab, une application métier.

**Ce que NovaTech dit de son activité**, dans l'épique « Refonte du tunnel de
commande » : *« NovaTech Industries commercialise des composants industriels
auprès de 340 clients professionnels. »*

Retenez ce chiffre. Il va servir.

### Les trois connecteurs et ce qu'ils exposent

| Connecteur | Contenu du périmètre de formation |
|---|---|
| **Jira**, projet `NOVA` | 14 tickets : une épique, des stories avec critères d'acceptation, un bug de production, des demandes commerciales |
| **Confluence**, espace `NOVA — Spécifications` | 4 pages : spécification fonctionnelle du tunnel de commande, règles de remise 2.4, normes de développement, architecture applicative |
| **GitLab**, `5.135.138.197:8300` | Le dépôt `atelier-copilot` : 25 classes Java, son historique, ses merge requests |

Le `mcp.json` du dépôt déclare `JIRA_PROJECTS_FILTER: NOVA` et
`CONFLUENCE_SPACES_FILTER: NOVA`. **Première question à se poser : que se
passe-t-il si on retire ces deux lignes ?**

## Étape 1 — La cartographie (12 min)

Pour chacun des trois connecteurs, remplissez cette fiche. Appuyez-vous sur le
dossier de données : ouvrez `jira/tickets.md`, lisez les pages de
`confluence/`. Ne cartographiez pas de mémoire — chaque ligne de la fiche
doit pouvoir citer un ticket ou une page.

| Rubrique | Ce qu'on y note |
|---|---|
| **Ce qui devient atteignable** | Deux questions, pas une. *Quelles données* : pas « Jira », mais quoi dans Jira — projets, champs, pièces jointes, commentaires. *Quelles actions* : ouvrez `outils/atlassian.md` — lire, mais aussi créer, modifier, supprimer, restreindre. Comptez. |
| **Ce que le filtre restreint** | Ce que `NOVA` écarte réellement — et ce qu'il n'écarte pas |
| **Sensibilité** | Le degré, et la raison. Pas une intuition : une justification |
| **Qui y a déjà accès** | Le connecteur n'accorde rien de plus que les droits du compte utilisé. Est-ce rassurant ? |
| **Ce qui ne doit jamais partir** | La liste, avec pour chaque entrée : pourquoi |

### Quatre questions qui guident la fiche

**1. Le connecteur ajoute-t-il des droits ?**

Non : il utilise le compte de l'utilisateur. Un développeur qui n'a pas accès à
l'espace RH ne le verra pas davantage via l'assistant.

Mais alors, **où est le risque ?** Formulez-le en une phrase. Cette phrase est
le cœur de votre fiche.

**2. Que contient réellement une page de spécification ?**

Ouvrez `confluence/02-regles-de-remise.md`, la page « Règles de remise » 2.4.
Relevez ce qu'elle contient au-delà
des règles elles-mêmes : des seuils de délégation nominatifs (« validation
explicite du directeur commercial », non délégable), la mention d'un fichier
Excel du service commercial « proscrit depuis le 1er septembre 2026 », et des
arbitrages de comité tarifaire.

Tout cela part au modèle quand quelqu'un demande « quelles sont les règles de
remise ? ». **Est-ce un problème ? Dans quel cas oui, dans quel cas non ?**

**3. Les outils d'écriture sont-ils réellement bloqués ?**

Le serveur Atlassian expose 98 outils. Dans `outils/atlassian.md`, comptez
ceux d'écriture — `jira_delete_issue`, `confluence_delete_page`,
`confluence_set_page_restrictions`… — puis relisez le `mcp.json` :
`READ_ONLY_MODE: "false"`.

Comparez avec le serveur GitLab : `GITLAB_READ_ONLY_MODE: "true"`. Ses outils
d'écriture sont **exposés mais bloqués**. Deux serveurs, deux réglages, et une
seule ligne les sépare. **Qui a décidé de cette ligne, et pourquoi n'est-elle
pas la même des deux côtés ?**

**4. Le filtre par projet suffit-il ?**

`JIRA_PROJECTS_FILTER: NOVA` limite aux tickets du projet NOVA. Dans
`jira/tickets.md`, cherchez le ticket intitulé **« Reprise des remises
négociées depuis le fichier commercial »**. Il est dans le périmètre autorisé — et il décrit un fichier
Excel contenant « les taux par client », avec doublons et lignes obsolètes.

> Les clés Jira sont attribuées à la création du projet : selon l'ordre
> d'import, les numéros varient d'une instance à l'autre. Désignez les tickets
> par leur titre, pas par leur clé.

**Le filtre protège des autres projets. Il ne protège pas de ce qui est
sensible à l'intérieur du périmètre autorisé.** Notez-le.

## Étape 2 — La règle d'arbitrage (12 min)

Une cartographie sans règle de décision ne sert à rien : elle décrit un risque
sans dire quoi en faire.

Classez ces **huit opérations** dans l'un des trois régimes. Pour chacune,
écrivez **la raison en une ligne** — c'est la raison qu'on vous demandera en
réunion, pas le classement.

| # | Opération |
|---|---|
| 1 | Jira — lire un ticket et lister ses ambiguïtés |
| 2 | Jira — publier un commentaire de résolution sur une story livrée |
| 3 | Jira — passer le statut d'un ticket à « Terminé » |
| 4 | Confluence — extraire les règles de calcul de la page « Règles de remise » |
| 5 | Confluence — mettre à jour la page d'architecture après un refactoring |
| 6 | GitLab — analyser une merge request et proposer des remarques |
| 7 | GitLab — pousser un commit de correction sur une branche dédiée |
| 8 | GitLab — fusionner une merge request dans `main` |

Les trois régimes :

- **AUTOMATIQUE** — l'assistant agit seul, on relit le résultat.
- **VALIDATION** — l'assistant propose, un humain approuve avant exécution.
- **INTERDIT** — l'assistant ne fait pas, quelle que soit la confiance.

### Les deux critères d'arbitrage

Ne classez pas à l'instinct. Appliquez deux critères, dans cet ordre :

**La réversibilité.** Combien coûte l'annulation ? Un commentaire Jira se
supprime, mais le destinataire l'a déjà lu et il est parti par e-mail. Un
commit sur une branche dédiée s'annule sans trace. Un merge sur `main`
déclenche une CI, peut-être un déploiement.

**L'effet externe.** L'action sort-elle de votre périmètre ? Une analyse reste
chez vous. Un commentaire Jira est vu par le PO et le client. C'est la
différence entre une erreur que vous corrigez et une erreur qu'on vous
reproche.

> **Un cas est volontairement piégeux** : l'opération 5. Mettre à jour une page
> de documentation paraît anodin — c'est de la doc, pas du code. Mais qui lit
> cette page, et pour décider de quoi ? Une documentation d'architecture fausse
> oriente des choix techniques pendant des mois, et personne ne remet en cause
> une page Confluence. Tranchez, et assumez votre raison.

## Étape 3 — La synthèse (6 min)

En **huit à douze lignes**, un document que vous pourriez envoyer à votre
responsable :

1. **Les deux risques prioritaires**, formulés comme des risques et non comme
   des peurs : « quoi peut arriver, dans quelles circonstances, avec quelle
   conséquence ».
2. **Les deux mesures à prendre en premier**, chacune avec son coût
   approximatif (une heure ? une réunion ? un changement d'outillage ?).
3. **Une chose que vous ne savez pas** et qu'il faudrait vérifier avant
   d'élargir l'usage.

Le point 3 est le plus important. Une analyse de risque qui ne comporte aucune
incertitude est une analyse qui n'a pas été faite sérieusement.

## Résultat attendu

Il n'y a **pas une seule bonne réponse**, mais des réponses défendables et des
réponses qui ne le sont pas. Ce qu'on attend de solide :

- **Sur la nature du risque** : le connecteur n'accorde aucun droit
  supplémentaire ; il change l'**échelle** et la **vitesse**. Ce qui demandait
  de parcourir trente pages devient une question de dix secondes. Un accès
  légitime mais fastidieux devient un accès légitime et instantané — et
  l'agrégation d'informations anodines produit de l'information qui ne l'est
  plus. C'est cette phrase qu'on attend, sous une forme ou une autre.
- **Sur le filtre** : `NOVA` protège du hors-périmètre, pas du sensible
  intra-périmètre. Le ticket de reprise du fichier commercial le prouve.
- **Sur les 340 clients** : le ticket de reprise parle d'un fichier de taux
  négociés client par client, la page de remises de validations nominatives.
  Le périmètre de formation est fictif ; le même connecteur sur le vrai Jira
  exposerait des conditions commerciales réelles — et 340 clients, c'est 340
  conditions.
- **Sur le classement** : les opérations 1, 4 et 6 sont des lectures ou des
  analyses — AUTOMATIQUE se défend. Les 2, 3, 5 et 7 ont un effet externe ou
  durable — VALIDATION. La 8 est INTERDIT chez la plupart des équipes, et
  celles qui l'autorisent le font sur un périmètre très borné avec une CI qui
  fait barrage.

Si un binôme classe autrement **et sait dire pourquoi**, c'est réussi. Si un
binôme classe comme ci-dessus sans savoir dire pourquoi, ça ne l'est pas.

## Ce qu'il faut retenir

Une politique d'usage qui n'est pas écrite repose sur la vigilance de chacun,
tous les jours, sans exception. C'est-à-dire sur rien.

Les deux documents que vous venez de produire ne protègent de rien par
eux-mêmes — ils ne s'exécutent pas. Ce qu'ils font, c'est rendre la décision
**explicite, discutable et révisable**. Une équipe qui a arbitré une fois n'a
plus à réarbitrer à chaque requête ; et quand le contexte change, elle sait
quel arbitrage revoir.

C'est la version « organisation » de ce que vous avez fait hier avec
`copilot-instructions.md` : ce qui est décidé une fois et partagé vaut mieux
que ce que chacun retape de mémoire.


