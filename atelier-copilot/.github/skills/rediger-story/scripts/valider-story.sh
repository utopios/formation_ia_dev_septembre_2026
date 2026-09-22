#!/bin/bash
# Valide la FORME d'une story au gabarit NovaTech. Lit la story sur stdin.
#
# Code de sortie 0 : conforme. 1 : non conforme, manquements listes sur stdout.
#
# Ce script ne juge pas le fond — il ne sait pas si une regle est juste. Il
# garantit qu'une story ne part pas en developpement avec une phrase de user
# story absente, des criteres non testables, un chiffre sans source ou un trou
# de gabarit. C'est un controle deterministe : meme entree, meme verdict,
# sans modele, sans reseau, en quelques millisecondes.
#
# TEST HORS COPILOT
#   ./valider-story.sh < references/exemple-nova-3.md ; echo $?   # 0
#   echo "En tant que client je veux une remise." | ./valider-story.sh ; echo $?   # 1

story=$(cat)
manquements=()

# Une puce peut courir sur plusieurs lignes (retour a 80 colonnes). On la
# recolle en une ligne logique : la source « (section n) » en fin de puce
# compte alors pour toute la puce, et un critere est juge en entier.
recoller() {
  awk '
    /^[-*] / { if (acc != "") print acc; acc = $0; next }
    /^[[:space:]]+[^[:space:]]/ && acc != "" { sub(/^[[:space:]]+/, " "); acc = acc $0; next }
    { if (acc != "") print acc; acc = ""; print }
    END { if (acc != "") print acc }
  '
}
story=$(printf '%s\n' "$story" | recoller)

# 1. La phrase de user story, sur une ou plusieurs lignes.
if ! printf '%s' "$story" | tr '\n' ' ' | grep -qiE "en tant qu[e'].{3,}je veux.{3,}afin d[e']"; then
  manquements+=("user story : la phrase « En tant que … je veux … afin de … » est absente ou incomplete")
fi

# 2. Au moins trois criteres d'acceptation, chacun verifiable.
#    Verifiable = contient un nombre, une valeur entre guillemets, ou un mot
#    d'etat / de message / de refus. « ca marche » ne passe pas.
criteres=$(printf '%s' "$story" | awk 'tolower($0) ~ /^##+ *crit/ {f=1; next} /^##/ {f=0} f && /^[-*] /')
nb=$(printf '%s\n' "$criteres" | grep -c '^[-*] ')
if [ "$nb" -lt 3 ]; then
  manquements+=("criteres d'acceptation : $nb trouve(s), 3 minimum")
fi
non_verifiables=$(printf '%s\n' "$criteres" | grep -viE '[0-9]|«|"|statut|état|etat|message|affich|refus|erreur|bloqu|rejet|inclus|exclu' | grep '^[-*] ' || true)
if [ -n "$non_verifiables" ]; then
  manquements+=("critere(s) non verifiable(s) — ni valeur, ni etat, ni message attendu :")
  while IFS= read -r l; do manquements+=("    $l"); done <<< "$non_verifiables"
fi

# 3. Chaque chiffre metier (pourcentage, montant, delai) est source ou marque
#    « A CONFIRMER ». On ne regarde que les lignes de regles et de criteres.
corps=$(printf '%s' "$story" | awk 'tolower($0) ~ /^##+ *(r.{1,2}gles|crit)/ {f=1; next} /^##/ {f=0} f')
non_sources=$(printf '%s\n' "$corps" | grep -E '[0-9]+ ?(%|EUR|€|jours?|heures?|h ouvr)' | grep -viE 'sections? [0-9]|À CONFIRMER|A CONFIRMER' || true)
if [ -n "$non_sources" ]; then
  manquements+=("valeur(s) chiffree(s) sans source ni <À CONFIRMER> :")
  while IFS= read -r l; do manquements+=("    $l"); done <<< "$non_sources"
fi

# 4. Aucun marqueur de gabarit reste vide. Seul <À CONFIRMER : …> est admis.
trous=$(printf '%s' "$story" | grep -oE '<[^>]{1,80}>' | grep -viE '^<(À|A) CONFIRMER' || true)
if [ -n "$trous" ]; then
  manquements+=("marqueur(s) de gabarit non remplis : $(printf '%s' "$trous" | tr '\n' ' ')")
fi

# 5. Les six sections, dans l'ordre.
# Les motifs tolerent un accent code sur un ou deux octets (.{1,2}) : le
# script rend le meme verdict en locale C et en UTF-8.
sections=$(printf '%s' "$story" | grep -E '^##+ ' | sed -E 's/^##+ *//' | tr '[:upper:]' '[:lower:]' | tr '\n' ' ')
for s in "contexte|Contexte" "user story|User story" "r.{1,2}gles m.{1,2}tier|Règles métier" "crit.{1,2}res|Critères d'acceptation" "hors p.{1,2}rim.{1,2}tre|Hors périmètre" "definition of done|Definition of done"; do
  motif="${s%%|*}"; libelle="${s#*|}"
  if ! printf '%s' "$sections" | grep -qE "$motif"; then manquements+=("section manquante : $libelle"); fi
done

# --- verdict -------------------------------------------------------------------
if [ ${#manquements[@]} -eq 0 ]; then
  echo "conforme : user story, $nb criteres verifiables, chiffres sources, gabarit complet"
  exit 0
fi
echo "NON CONFORME — ${#manquements[@]} point(s) :"
printf '  - %s\n' "${manquements[@]}"
exit 1
