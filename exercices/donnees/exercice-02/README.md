# Donnees de l'Exercice 2 — le SI NovaTech, hors ligne

Ce dossier est une **photographie** de ce que les trois connecteurs MCP de
la formation exposent. Il vous permet de cartographier sans attendre le jeton
Atlassian, qui sera distribue cet apres-midi pour la Demo 6b.

```
jira/tickets.md          les 14 tickets du projet NOVA
confluence/*.md          les 4 pages de l'espace « NOVA — Specifications »
gitlab/apercu.md         ce que le connecteur GitLab voit, et ou le trouver
outils/atlassian.md      les 98 outils du serveur Atlassian, lecture / ecriture
outils/gitlab.md         les 65 outils du serveur GitLab, lecture / ecriture
```

Le `.vscode/mcp.json` a etudier est celui de votre depot `atelier-copilot` :
c'est lui qui declare les filtres `JIRA_PROJECTS_FILTER` et
`CONFLUENCE_SPACES_FILTER`.

**Ce que ce dossier n'est pas** : une copie complete du SI. Il contient ce
que les filtres laissent passer. La question « que verrait-on sans les
filtres ? » fait partie de l'exercice — et la reponse n'est pas ici.

Genere par `simulation/exporter_dossier_exercice02.py` a partir des memes
sources que Jira et Confluence : ce qui est ici est ce qui est la-bas.
