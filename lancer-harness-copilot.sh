#!/bin/bash
# Lance le harnais Copilot SDK (Demo 15) depuis ce dossier formation/.
#
# Prerequis, une seule fois :
#   - le CLI Copilot : npm install -g @github/copilot   (ou npm install --prefix ~/.copilot-cli @github/copilot)
#   - connecte : copilot  puis  /login
#   - identifiants Atlassian dans ~/.netrc (machine ...atlassian.net login ... password ...)
# Si copilot n'est pas dans le PATH : export COPILOT_CLI=/chemin/vers/copilot
if [ -z "$COPILOT_CLI" ] && [ -x "$HOME/.copilot-cli/node_modules/.bin/copilot" ]; then
  export COPILOT_CLI="$HOME/.copilot-cli/node_modules/.bin/copilot"
fi
cd "$(dirname "$0")/java" || exit 1
mvn -q compile exec:java -Dexec.mainClass="fr.utopios.formation.demos.DemoHarnessCopilot" 2>&1 | grep -vE 'WARNING|SLF4J'
