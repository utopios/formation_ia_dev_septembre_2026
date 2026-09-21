---
name: eval-prompt
description: Mesure la qualité d'un gabarit de prompt d'extraction d'avis (score de schéma, clés en trop, comparaison avant/après). À utiliser dès qu'on modifie, compare ou évalue un fichier de src/main/resources/prompts, ou qu'on demande « est-ce que ce prompt est meilleur ? ».
---
# Évaluer un gabarit de prompt

Un prompt ne se juge pas à la lecture : il se mesure. Ce skill décrit la procédure de mesure du dépôt.

## Procédure
1. **Mesure sur le modèle cible** (référence) :
   ```bash
   mvn -q compile exec:java                                # Ollama, llama3.2:1b
   mvn -q compile exec:java -Dexec.args="--hors-ligne"     # sans Ollama, sorties rejouées
   ```
   Relève le score et les clés en trop de chaque niveau.
2. **Mesure d'une sortie isolée** : enregistre la sortie brute du modèle dans un fichier, puis
   ```bash
   python3 mcp/scoring.py sortie.txt
   ```
3. **Interprète** avec la [grille de lecture](./grille.md).
4. **Compare avant/après** dans un tableau : version du gabarit, score, clés en trop, régressions.

## Règles
- Ne change qu'une chose à la fois dans le gabarit, sinon la mesure n'explique rien.
- Un score de 1.00 avec des clés en trop n'est pas un succès complet : signale-le.
- Ne conclus jamais à partir d'un seul avis : utilise `data/avis.jsonl`, y compris le cas d'injection (id 6).
- Rapporte les chiffres réellement obtenus. Si une commande échoue, dis-le et donne l'erreur.
