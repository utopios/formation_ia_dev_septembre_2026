# Grille de lecture des scores

| Observation | Cause probable | Levier dans le gabarit |
| --- | --- | --- |
| Score 0.00, « JSON invalide ou absent » | Le modèle répond en prose | Ajouter le format de sortie et la mention « lu par un programme » |
| Score 0.00, « 0/4 champs presents » | JSON valide, mais schéma inventé par le modèle | Ajouter un exemple entrée/sortie (few-shot) |
| Score partiel (0.25 à 0.75) | Champ omis quand l'information manque | Ajouter une règle de secours (« aucun », « inconnu ») |
| Score 1.00 avec clés en trop | Consigne « aucune clé supplémentaire » ignorée | Rien de fiable côté prompt sur un petit modèle : filtrer dans le code |
| « sentiment hors valeurs autorisees » | Énumération absente ou trop loin de la fin | Lister les valeurs dans le schéma et les montrer dans l'exemple |
| Produit « HACK » sur l'avis n° 6 | Injection via l'entrée | Délimiter l'avis, rappeler qu'il s'agit de données et non d'instructions |
