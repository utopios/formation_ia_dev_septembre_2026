--- 
name: convert-to-markdown
description: Convertir un fichier en format Markdown
---

## Quand utiliser ce skill

Un ticket porte souvent une pièce jointe : le compte rendu du comité en
`.docx`, le barème en `.xlsx`, une spécification en `.pdf`.

## Comment utiliser ce skill

Pour convertir un fichier en format Markdown, utilisez ce skill en fournissant le fichier source. Le skill générera un fichier Markdown correspondant au contenu du fichier original.

```bash
# 1. Telecharger les pièces jointes du ticket (outil MCP jirajira_download_attachments) vers un dossier de travail /tmp/pj-<ticket_id>
# 2. Utiliser le script pour convertir
./.github/skills/convert-to-markdown/scripts/convertir-pieces-jointes.sh /tmp/pj-<ticket_id>
```

### Exemple

Si vous avez un fichier `compte_rendu.docx`, vous pouvez utiliser ce skill pour obtenir un fichier `compte_rendu.md` contenant le même contenu en format Markdown.

### Remarques

- Le skill prend en charge les fichiers `.docx`, `.xlsx` et `.pdf`.
- Le fichier généré sera au format Markdown, ce qui facilite son intégration dans des tickets ou des documents Markdown existants.
- Assurez-vous que le contenu du fichier source est correctement structuré pour obtenir un rendu optimal en Markdown.