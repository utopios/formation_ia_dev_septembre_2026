#!/bin/bash
# Hook PreToolUse : le perimetre NOVA, en CODE.
#
# POURQUOI
# JIRA_PROJECTS_FILTER et CONFLUENCE_SPACES_FILTER, dans .vscode/mcp.json,
# sont de la configuration : une ligne, modifiable par quiconque a acces au
# depot, et ignoree si un autre serveur MCP est branche. Ce hook applique la
# meme regle a chaque appel, quelle que soit la configuration.
#
# CE QU'IL JUGE, ET RIEN D'AUTRE
# Le perimetre. Il ne refuse pas une ecriture (c'est politique-lecture), il
# ne cherche pas de secret (c'est anti-fuite). Deux hooks, deux
# responsabilites : celui qui les fusionne ne sait plus lequel a refuse.
#
#   jira_*       : un projet autre que NOVA dans le JQL ou une cle de ticket -> deny
#   confluence_* : un espace autre que NOVA dans la requete ou space_key      -> deny
#   tout le reste : allow (sortie explicite)
#
# Lit les deux dialectes (toolName/toolArgs, tool_name/tool_input), repond dans
# les deux, code 2 sur refus, aucun appel reseau.

charge=$(cat)
outil=$(printf '%s' "$charge" | grep -oE '"(toolName|tool_name)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
court=$(printf '%s' "$outil" | sed -E 's#^.*/##; s/^mcp_[a-z0-9]+_//')
PERIMETRE="NOVA"

decision="allow"; raison=""

repondre() {
  local r; r=$(printf '%s' "$raison" | sed 's/\\/\\\\/g; s/"/\\"/g')
  printf '{"permissionDecision":"%s","permissionDecisionReason":"%s","hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"%s","permissionDecisionReason":"%s"}}\n' "$decision" "$r" "$decision" "$r"
  printf '%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$decision" "$court" "$raison" >> "$(dirname "$0")/../perimetre.log" 2>/dev/null
  [ "$decision" = "deny" ] && exit 2
  exit 0
}

case "$court" in
  jira_*)
    # Les guillemets JSON arrivent echappes (\"RH\") : on retire les barres
    # obliques avant de lire le JQL.
    charge=$(printf '%s' "$charge" | tr -d '\\')
    # « project != NOVA » ou « project not in (NOVA) » vise tout SAUF le
    # perimetre : c'est une sortie de perimetre par negation.
    if printf '%s' "$charge" | grep -qiE 'project[[:space:]]*(!=|not[[:space:]]+in)'; then
      decision="deny"; raison="Perimetre : un JQL en negation (project != / not in) sort de NOVA par construction."; repondre
    fi
    # « project = RH » donne un projet ; « project in (NOVA, RH) » en donne
    # plusieurs : on capture toute la parenthese, puis on la decoupe.
    projets=$(printf '%s' "$charge" \
      | grep -oiE 'project[[:space:]]*(=|in)[[:space:]]*(\([^)]*\)|"?[A-Za-z][A-Za-z0-9]*"?)' \
      | sed -E 's/^[Pp][Rr][Oo][Jj][Ee][Cc][Tt][[:space:]]*(=|[Ii][Nn])[[:space:]]*//' \
      | tr '(),"' '    ' | tr -s ' ' '\n' | grep -E '^[A-Za-z][A-Za-z0-9]*$' \
      | tr '[:lower:]' '[:upper:]' | sort -u)
    for p in $projets; do
      if [ "$p" != "$PERIMETRE" ]; then
        decision="deny"; raison="Perimetre : le projet Jira « $p » est hors NOVA. Ce depot ne travaille que sur NOVA."; repondre
      fi
    done
    # Cles de ticket : RH-12, NOVA-2. Le prefixe est le projet.
    cles=$(printf '%s' "$charge" | grep -oE '\b[A-Z][A-Z0-9]+-[0-9]+\b' | sed -E 's/-[0-9]+$//' | sort -u)
    for p in $cles; do
      if [ "$p" != "$PERIMETRE" ]; then
        decision="deny"; raison="Perimetre : la cle « $p-… » designe un projet hors NOVA."; repondre
      fi
    done
    ;;
  confluence_*)
    espaces=$(printf '%s' "$charge" | grep -oiE '("space_key"[[:space:]]*:[[:space:]]*"|space[[:space:]]*=[[:space:]]*"?)[A-Za-z][A-Za-z0-9]*' | grep -oE '[A-Za-z][A-Za-z0-9]*$' | tr '[:lower:]' '[:upper:]' | sort -u)
    for e in $espaces; do
      if [ "$e" != "$PERIMETRE" ]; then
        decision="deny"; raison="Perimetre : l'espace Confluence « $e » est hors NOVA."; repondre
      fi
    done
    ;;
esac

raison="Perimetre NOVA respecte."
repondre
