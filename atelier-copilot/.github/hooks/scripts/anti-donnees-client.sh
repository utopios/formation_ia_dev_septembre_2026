#!/bin/bash
# Hook PreToolUse : aucune donnee client ne part dans un appel d'outil.
#
# POURQUOI
# anti-fuite arrete les secrets TECHNIQUES : jetons, cles, mots de passe. Ce
# hook arrete les donnees PERSONNELLES ET COMMERCIALES : une adresse e-mail,
# un SIREN, un IBAN colles dans le chat et partis vers un fournisseur. Les
# deux tournent ; ils ne se remplacent pas.
#
# LE FAUX POSITIF, ET COMMENT ON LE TRAITE
# Un SIREN fait neuf chiffres. Un identifiant de page Confluence aussi
# (page_id 360449 en fait six, mais d'autres en font neuf), un montant en
# centimes peut en faire neuf. On ne peut pas les distinguer par la forme.
# On distingue par le CONTEXTE :
#   - un nombre porte par un champ d'identifiant technique (page_id, id, iid,
#     issue_id, project_id, commit, sha…) n'est jamais un SIREN ;
#   - un nombre de neuf chiffres dans un champ de TEXTE (body, summary,
#     description, query, content, comment, jql…) est traite comme un SIREN.
# Ce qu'on ne sait pas distinguer : un vrai SIREN colle dans un champ
# d'identifiant. On assume ce trou, et on le documente ici plutot que de
# pretendre le couvrir.
#
# Lit les deux dialectes, repond dans les deux, code 2 sur refus.

charge=$(cat)
decision="allow"; raison=""

repondre() {
  local r; r=$(printf '%s' "$raison" | sed 's/\\/\\\\/g; s/"/\\"/g')
  printf '{"permissionDecision":"%s","permissionDecisionReason":"%s","hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"%s","permissionDecisionReason":"%s"}}\n' "$decision" "$r" "$decision" "$r"
  [ "$decision" = "deny" ] && exit 2
  exit 0
}

# 1. E-mail : sans ambiguite, dans n'importe quel champ.
if printf '%s' "$charge" | grep -qE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'; then
  decision="deny"; raison="Donnee client : une adresse e-mail est presente dans l'appel. Retirez-la ou remplacez-la par un identifiant interne."; repondre
fi

# 2. IBAN : deux lettres, deux chiffres, puis 11 a 30 caracteres alphanumeriques.
if printf '%s' "$charge" | grep -qE '\b[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}\b'; then
  decision="deny"; raison="Donnee client : un IBAN est present dans l'appel. Rien n'a ete transmis."; repondre
fi

# 3. SIREN : neuf chiffres, mais seulement hors des champs d'identifiants.
#    On retire d'abord les champs techniques, puis on cherche.
texte=$(printf '%s' "$charge" | sed -E 's/"(page_id|id|iid|issue_id|project_id|parent_id|commit_id|sha|attachment_id|comment_id|version|timestamp|toolCallId|tool_use_id)"[[:space:]]*:[[:space:]]*"?[A-Za-z0-9]+"?//g')
if printf '%s' "$texte" | grep -qE '(^|[^0-9])[0-9]{9}([^0-9]|$)'; then
  decision="deny"; raison="Donnee client : un numero a neuf chiffres (SIREN probable) est present dans un champ de texte. Si c'est un identifiant technique, passez-le dans un champ d'identifiant, pas dans du texte libre."; repondre
fi

raison="Aucune donnee client detectee."
repondre
