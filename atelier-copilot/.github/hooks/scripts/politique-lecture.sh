#!/bin/bash
# Hook preToolUse : politique « lecture seule » pour un agent d'analyse.
#
# CE QUE FAIT CE SCRIPT
# Il recoit sur stdin l'appel d'outil que l'agent s'apprete a faire, et rend
# une decision : allow, ask ou deny. La decision est du CODE, pas une phrase
# dans un prompt : elle s'applique meme si l'agent a ete mal ecrit, meme si
# l'utilisateur insiste.
#
# DEUX DIALECTES DE CHARGE
# Copilot envoie soit le format natif (camelCase) soit le format compatible
# VS Code (snake_case). Le script lit les deux, et repond dans les deux :
#   entree  : {"toolName": "...", "toolArgs": {...}}         (natif)
#             {"tool_name": "...", "tool_input": {...}}      (VS Code)
#   sortie  : {"permissionDecision": "...", "permissionDecisionReason": "...",
#              "hookSpecificOutput": {"hookEventName": "PreToolUse",
#                                     "permissionDecision": "...", ...}}
#
# CODES DE SORTIE (reference Copilot)
#   0 : la decision JSON sur stdout fait foi
#   2 : refus « fail-closed » — refuse meme si le JSON dit allow
#   autre : erreur du hook -> refus, avec un message generique
#   timeout : FAIL-OPEN, l'outil passe. Un hook lent est un hook absent :
#             ce script ne fait aucun appel reseau et se termine en millisecondes.
#
# TEST HORS COPILOT
#   echo '{"toolName":"jira_get_issue"}'    | ./politique-lecture.sh ; echo $?
#   echo '{"toolName":"jira_add_comment"}'  | ./politique-lecture.sh ; echo $?

charge=$(cat)

# --- 1. Extraire le nom de l'outil, quel que soit le dialecte -----------------
outil=$(printf '%s' "$charge" \
  | grep -oE '"(toolName|tool_name)"[[:space:]]*:[[:space:]]*"[^"]+"' \
  | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
[ -z "$outil" ] && outil="inconnu"

# Un outil MCP arrive souvent prefixe par son serveur : « atlassian/jira_get_issue »
# ou « mcp_atlassian_jira_get_issue ». On raisonne sur le nom court.
court=$(printf '%s' "$outil" | sed -E 's#^.*/##; s/^mcp_[a-z0-9]+_//')

# --- 2. Decider ----------------------------------------------------------------
# Trois listes explicites, puis une heuristique sur le verbe pour tout le reste.
# L'ordre compte : une regle explicite l'emporte sur l'heuristique.

decision="ask"
raison="Outil non classe par la politique : confirmation demandee."

case "$court" in
  # Lectures connues : Jira, Confluence, GitLab, outils integres de lecture.
  jira_get_issue|jira_search|jira_get_transitions|jira_get_all_projects|\
  confluence_get_page|confluence_search|confluence_get_page_children|\
  confluence_get_space_page_tree|confluence_get_page_history|\
  get_merge_request|get_merge_request_diffs|list_merge_request_changed_files|\
  get_file_contents|mr_discussions|get_merge_request_approval_state|\
  read|search|grep|glob|view|codebase|fetch|search_code_subagent)
    decision="allow"; raison="Lecture : autorisee par la politique." ;;

  # Ecritures connues : refus ferme, avec la raison qui remonte a l'agent.
  jira_add_comment|jira_update_issue|jira_transition_issue|jira_create_issue|\
  jira_delete_issue|confluence_update_page|confluence_create_page|\
  confluence_delete_page|confluence_set_page_restrictions|\
  create_merge_request|merge_merge_request|create_note|approve_merge_request|\
  edit|create|editFiles|createFile|write|bash|runInTerminal)
    decision="deny"
    raison="Cet agent est en lecture seule : « $court » ecrit ou execute. Propose la modification a l'utilisateur au lieu de la faire." ;;

  # Heuristique sur le verbe, pour les outils qu'on n'a pas listes. On cherche
  # le verbe comme un JETON delimite par « _ », en tete ou au milieu du nom :
  # « move_page » comme « confluence_move_page ». Le refus l'emporte : un nom
  # qui contient a la fois un verbe de lecture et un d'ecriture est refuse.
  *)
    # « merge_request » est un NOM, pas le verbe « merge » : sans cette ligne,
    # list_merge_request_pipelines serait refuse. L'heuristique sur les noms
    # est fragile — c'est exactement pourquoi les cas connus sont listes
    # explicitement au-dessus, et pourquoi l'inconnu tombe en « ask ».
    verbes=$(printf '%s' "$court" | sed -E 's/merge_requests?//g')
    if printf '%s' "$verbes" | grep -qE '(^|_)(create|update|delete|add|set|upload|move|remove|merge|push|approve|transition|reply|copy|edit|write|post|fork|assign|close|reopen|link|batch)(_|$)'; then
      decision="deny"; raison="Ecriture (d'apres le nom) : « $court » est refuse par la politique lecture seule."
    elif printf '%s' "$verbes" | grep -qE '(^|_)(get|list|search|read|find|fetch|view|download|check|verify|count)(_|$)'; then
      decision="allow"; raison="Lecture (d'apres le nom) : autorisee."
    fi ;;
esac

# --- 3. Repondre dans les deux dialectes ---------------------------------------
# Le JSON est ecrit a la main : pas de dependance a jq, qui n'est pas partout.
raison_json=$(printf '%s' "$raison" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf '{"permissionDecision":"%s","permissionDecisionReason":"%s","hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"%s","permissionDecisionReason":"%s"}}\n' \
  "$decision" "$raison_json" "$decision" "$raison_json"

# Journal cote formateur : une ligne par decision, sans le contenu des arguments.
printf '%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$decision" "$court" \
  >> "$(dirname "$0")/../politique.log" 2>/dev/null

# Refus : code 2, le « fail-closed » documente. Le JSON ci-dessus porte la raison.
[ "$decision" = "deny" ] && exit 2
exit 0
