#!/bin/bash
# Hook preToolUse : refuse un appel d'outil dont la charge contient un secret.
#
# Ce hook est du CODE. Contrairement a une consigne ecrite dans un agent, il
# ne depend pas de la bonne volonte du modele : il s'execute toujours, meme si
# l'agent a ete mal ecrit, meme si l'utilisateur insiste.
#
# Le harness passe la charge de l'appel sur stdin, en JSON.
# Code de sortie 0 : autorise. Code 2 : bloque, le message stderr est rendu.

charge=$(cat)

# Motifs de secrets. Volontairement courts et lisibles : un hook qu'on ne
# comprend pas en dix secondes ne sera pas maintenu.
MOTIFS=(
  'glpat-[A-Za-z0-9_-]{20}'          # jeton GitLab
  'ATATT[A-Za-z0-9_=-]{20}'          # jeton Atlassian
  'AKIA[0-9A-Z]{16}'                 # cle AWS
  '-----BEGIN [A-Z ]*PRIVATE KEY'    # cle privee
  '(password|passwd|api[_-]?key|secret|token)[\\"'"'"']*\s*[:=]\s*[\\"'"'"']*[A-Za-z0-9_!@#$%^&*-]{8,}'
)

for motif in "${MOTIFS[@]}"; do
  # -e est indispensable : sans lui, un motif commencant par « - » est pris
  # pour une option de grep et le hook echoue silencieusement.
  if printf '%s' "$charge" | grep -qE -e "$motif"; then
    echo "BLOQUE : un secret a ete detecte dans cet appel d'outil." >&2
    echo "Motif declencheur : $motif" >&2
    # La raison sur stdout, en JSON, dans les deux dialectes : c'est elle
    # que l'agent recoit. stderr ne va qu'a l'utilisateur et au journal.
    raison="Secret detecte dans l'appel (motif $motif). Rien n'a ete transmis : retirez le secret ou utilisez une reference de configuration."
    printf '{"permissionDecision":"deny","permissionDecisionReason":"%s","hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$raison" "$raison"
    # Code 2 : refus « fail-closed » documente — refuse meme si le JSON disait allow.
    exit 2
  fi
done

printf '{"permissionDecision":"allow","hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}\n'
exit 0
