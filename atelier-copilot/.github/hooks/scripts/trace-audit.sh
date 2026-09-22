#!/bin/bash
# Hook postToolUse : journalise CE QUI A ETE APPELE, jamais ce qui a ete lu.
#
# Le point pedagogique tient en une ligne : on trace les METADONNEES, pas le
# contenu. Journaliser le texte des tickets et des pages Confluence, c'est
# recreer une copie du corpus de l'entreprise dans un fichier de log, souvent
# moins bien protege que le corpus lui-meme.
#
# Ce que la trace permet : rejouer, auditer, repondre a « qu'est-ce que
# l'assistant a consulte le 12 mars ? ».
# Ce qu'elle ne permet pas : relire les donnees. C'est voulu.

charge=$(cat)
JOURNAL=".github/hooks/audit.log"

# Nom de l'outil uniquement. Si jq est absent, on se rabat sur grep.
if command -v jq >/dev/null 2>&1; then
  outil=$(printf '%s' "$charge" | jq -r '.tool // .name // "inconnu"' 2>/dev/null)
else
  outil=$(printf '%s' "$charge" | grep -oE '"(tool|name)"[[:space:]]*:[[:space:]]*"[^"]+"' \
          | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
fi
[ -z "$outil" ] && outil="inconnu"

# Horodatage ISO, nom de l'outil, taille de la reponse. Rien d'autre.
printf '%s\t%s\t%d octets\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$outil" "${#charge}" >> "$JOURNAL"

exit 0
