# GitLab — ce que le connecteur expose

Le depot `atelier-copilot`, que vous avez deja sur votre poste, EST le
contenu du GitLab de formation (`http://5.135.138.197:8300/root/atelier-copilot`).
Vous n'avez donc pas besoin d'un extrait : ouvrez-le.

Ce qu'un connecteur GitLab remonte, au-dela des fichiers :

| Element | Visible par le connecteur ? | Ou le trouver localement |
|---|---|---|
| Le code source, toutes branches | oui | `src/` |
| L'historique des commits | oui | `git log` — y compris les fichiers **supprimes** |
| Les merge requests, leurs diffs, leurs commentaires | oui | pas en local : c'est cote serveur |
| Les pipelines CI et leurs journaux | oui | pas en local |
| Les variables CI/CD protegees | selon les droits du jeton | jamais en local |

**Deux points a garder pour la cartographie :**

1. Un historique Git contient les fichiers supprimes. Un secret commite puis
   retire dans un commit suivant reste lisible pour quiconque lit
   l'historique — et le connecteur le lit.
2. L'API GitLab est fermee sans jeton (`[]` en anonyme), mais le clone Git
   anonyme fonctionne. Deux chemins d'acces, deux perimetres : le connecteur
   MCP passe par l'API, donc par un jeton, donc par les droits de ce jeton.
