#!/bin/bash
# Convertit en Markdown les pieces jointes bureautiques d'un dossier.
set -uo pipefail

SOURCE="${1:-}"
SORTIE="${2:-$SOURCE}"

if [ -z "$SOURCE" ] || [ ! -d "$SOURCE" ]; then
  echo "Dossier introuvable : ${SOURCE:-<aucun>}" >&2
  echo "Usage : $0 <dossier> [dossier-sortie]" >&2
  exit 1
fi

if ! command -v markitdown >/dev/null 2>&1; then
  echo "markitdown est introuvable." >&2
  echo "Activez le venv puis installez-le — les crochets sont necessaires :" >&2
  echo "  source ~/.venv-formation/bin/activate" >&2
  echo "  pip install 'markitdown[docx,pptx,xlsx,pdf]'" >&2
  exit 2
fi

mkdir -p "$SORTIE"

convertis=0
ignores=0
echecs=0

# Les formats que markitdown sait lire. Un .txt ou un .md est deja du texte :
# le convertir ne demontre rien et ferait perdre du temps.
for fichier in "$SOURCE"/*.pdf "$SOURCE"/*.docx "$SOURCE"/*.pptx "$SOURCE"/*.xlsx; do
  [ -e "$fichier" ] || continue

  base=$(basename "$fichier")
  cible="$SORTIE/${base%.*}.md"

  if markitdown "$fichier" -o "$cible" 2>/dev/null; then
    octets_src=$(wc -c < "$fichier" | tr -d ' ')
    octets_md=$(wc -c < "$cible" | tr -d ' ')
    if [ "$octets_md" -gt 0 ]; then
      ratio=$(awk -v a="$octets_src" -v b="$octets_md" 'BEGIN{printf "%.1f", a/b}')
      printf '%-38s %8s -> %7s octets  (%sx)\n' "$base" "$octets_src" "$octets_md" "$ratio"
      convertis=$((convertis + 1))
    else
      # Cas frequent : un PDF scanne, sans couche texte. La conversion
      # "reussit" et produit un fichier vide. Le signaler plutot que de
      # laisser croire a une extraction reussie.
      printf '%-38s conversion vide — PDF scanne ? un OCR serait necessaire\n' "$base"
      rm -f "$cible"
      echecs=$((echecs + 1))
    fi
  else
    printf '%-38s ECHEC de conversion\n' "$base"
    echecs=$((echecs + 1))
  fi
done

# Ce que markitdown ne sait pas lire : on le dit, on ne le passe pas sous silence.
for fichier in "$SOURCE"/*; do
  [ -f "$fichier" ] || continue
  minuscules=$(printf '%s' "$fichier" | tr '[:upper:]' '[:lower:]')
  case "$minuscules" in
    *.pdf|*.docx|*.pptx|*.xlsx|*.md) continue ;;
  esac
  printf '%-38s ignore (format non pris en charge)\n' "$(basename "$fichier")"
  ignores=$((ignores + 1))
done

echo
echo "$convertis converti(s), $echecs echec(s), $ignores ignore(s)."

if [ "$convertis" -gt 0 ]; then
  echo "Travaillez desormais sur les .md : le binaire n'a plus a etre transmis."
fi

exit 0
