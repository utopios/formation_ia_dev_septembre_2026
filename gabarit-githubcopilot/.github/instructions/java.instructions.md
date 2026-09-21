---
name: Conventions Java
description: Règles de style et de conception pour le code Java de production
applyTo: "src/main/java/**/*.java"
---
- Java 21 : records, `switch` à flèches, pattern matching pour `instanceof`, text blocks.
- `Optional` en retour quand l'absence est un cas normal ; jamais `null` en retour de méthode publique.
- Collections immuables en sortie (`List.of`, `List.copyOf`).
- Une Javadoc d'une à trois lignes sur chaque type public, qui dit le pourquoi et non le comment.
- Formatage numérique : toujours `Locale.ROOT` dans `printf` et `String.format`.
- Pas de nouvelle dépendance Maven sans que l'utilisateur l'ait demandée.
