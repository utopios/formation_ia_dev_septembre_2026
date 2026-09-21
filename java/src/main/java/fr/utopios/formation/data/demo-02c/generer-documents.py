#!/usr/bin/env python3
"""Genere les documents bureautiques de la Demo 2c.

Trois fichiers, tous derives de la page Confluence « Regles de remise » 2.4
(`simulation/confluence/02-regles-de-remise.md`) pour rester coherents avec
`DiscountPolicy` de l'atelier Copilot et avec la Demo 6.

    specification-remises.docx   la spec fonctionnelle, en Word
    comite-tarifaire.pptx        le deck du comite, en PowerPoint
    bareme-remises.xlsx          le bareme, en tableur

Ces fichiers sont volontairement mis en forme (styles, tableaux, couleurs,
metadonnees d'auteur) : c'est ce balisage, invisible a la lecture, qui pese
lourd en tokens et que la conversion en Markdown fait disparaitre.

Usage :
    python3 generer-documents.py

Dependances : python-docx, python-pptx, openpyxl.
"""

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt, RGBColor
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from pptx import Presentation
from pptx.util import Inches, Pt as PptPt

ICI = Path(__file__).parent

BLEU = RGBColor(0x1F, 0x4E, 0x79)


def generer_docx() -> Path:
    """La specification fonctionnelle, telle que le metier la redige."""
    doc = Document()

    # Metadonnees : elles partent avec le fichier, et personne ne les relit.
    props = doc.core_properties
    props.author = "Direction commerciale NovaTech Industries"
    props.title = "Regles de remise - version 2.4"
    props.comments = (
        "Document de travail. Contient les arbitrages du comite tarifaire "
        "du 28 aout 2026, y compris ceux non retenus."
    )
    props.category = "Interne - diffusion restreinte"

    titre = doc.add_heading("Regles de remise", level=0)
    titre.alignment = WD_ALIGN_PARAGRAPH.CENTER

    st = doc.add_paragraph()
    run = st.add_run("Version 2.4 - applicable au 1er septembre 2026")
    run.italic = True
    run.font.size = Pt(11)
    run.font.color.rgb = BLEU
    st.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph(
        "Proprietaire : Direction commerciale. Statut : approuvee, validee "
        "en comite tarifaire du 28 aout 2026."
    )

    doc.add_heading("1. Principe general", level=1)
    doc.add_paragraph(
        "Une commande ne beneficie que d'une seule remise : la plus "
        "avantageuse pour le client parmi celles auxquelles il a droit. Les "
        "remises ne se cumulent jamais entre elles, sans exception."
    )
    doc.add_paragraph(
        "Cette regle de non-cumul est absolue. Toute demande de derogation "
        "releve du comite tarifaire et fait l'objet d'un avenant."
    )

    doc.add_heading("2. Bareme de remise par volume", level=1)
    doc.add_paragraph(
        "Calcule sur le montant HT des articles, frais de port exclus."
    )

    t = doc.add_table(rows=1, cols=2)
    t.style = "Light Grid Accent 1"
    en_tete = t.rows[0].cells
    en_tete[0].text = "Montant HT des articles"
    en_tete[1].text = "Taux de remise"
    for montant, taux in [
        ("Inferieur a 1 000 EUR", "0 %"),
        ("De 1 000 EUR inclus a 5 000 EUR exclu", "3 %"),
        ("De 5 000 EUR inclus a 20 000 EUR exclu", "6 %"),
        ("A partir de 20 000 EUR inclus", "9 %"),
    ]:
        ligne = t.add_row().cells
        ligne[0].text = montant
        ligne[1].text = taux

    doc.add_paragraph()
    doc.add_paragraph("Bonus de categorie, ajoute au taux du palier :")
    t2 = doc.add_table(rows=1, cols=2)
    t2.style = "Light Grid Accent 1"
    e2 = t2.rows[0].cells
    e2[0].text = "Categorie"
    e2[1].text = "Bonus"
    for cat, bonus in [
        ("Standard", "0 point"),
        ("Privilege", "2 points de pourcentage"),
        ("Grand compte", "4 points de pourcentage"),
    ]:
        ligne = t2.add_row().cells
        ligne[0].text = cat
        ligne[1].text = bonus

    doc.add_heading("3. Plafond de remise", level=1)
    p = doc.add_paragraph()
    run = p.add_run(
        "Le taux de remise total ne depasse en aucun cas 15 %."
    )
    run.bold = True
    doc.add_paragraph(
        "Toute remise superieure a 15 % est interdite au niveau applicatif : "
        "elle doit etre rejetee par le calculateur, et non pas simplement "
        "signalee."
    )
    doc.add_paragraph(
        "Un depassement ne peut etre autorise que par une validation "
        "explicite du directeur commercial, saisie dans l'outil et "
        "horodatee. Cette validation est nominative : elle ne peut etre "
        "deleguee ni au responsable commercial, ni a l'administration des "
        "ventes."
    )

    doc.add_heading("4. Seuils de delegation", level=1)
    t3 = doc.add_table(rows=1, cols=2)
    t3.style = "Light Grid Accent 1"
    e3 = t3.rows[0].cells
    e3[0].text = "Taux de remise demande"
    e3[1].text = "Qui valide"
    for taux, qui in [
        ("Jusqu'a 8 %", "Aucune validation, application automatique"),
        ("Plus de 8 % et jusqu'a 12 %", "Responsable commercial"),
        ("Plus de 12 % et jusqu'a 15 %", "Directeur commercial"),
        ("Plus de 15 %", "Interdit (voir section 3)"),
    ]:
        ligne = t3.add_row().cells
        ligne[0].text = taux
        ligne[1].text = qui

    doc.add_paragraph()
    doc.add_paragraph(
        "Delai de reponse attendu du validateur : 48 heures ouvrees. Sans "
        "reponse au terme de ce delai, la demande est refusee et le "
        "commercial en est informe. Il n'existe pas de validation tacite."
    )

    doc.add_heading("5. Remises contractuelles des grands comptes", level=1)
    for point in [
        "Le taux contractuel est la reference : il remplace le bareme par "
        "volume, il ne s'y ajoute pas.",
        "Si le bareme par volume est plus avantageux que le taux contractuel "
        "sur une commande donnee, c'est le bareme qui s'applique.",
        "Le taux contractuel reste lui aussi soumis au plafond de 15 %.",
        "La source de verite du taux contractuel est l'application, et non "
        "le fichier Excel du service commercial, dont l'usage est proscrit "
        "depuis le 1er septembre 2026.",
    ]:
        doc.add_paragraph(point, style="List Bullet")

    doc.add_heading("6. Remise de premiere commande", level=1)
    doc.add_paragraph(
        "Un nouveau client professionnel beneficie de 5 % sur sa premiere "
        "commande, valable 30 jours a compter de la validation de son compte."
    )
    for point in [
        "Non cumulable, conformement a la section 1.",
        "Une seule fois par entite juridique, identifiee par son numero "
        "SIREN. Deux comptes partageant le meme SIREN n'ouvrent pas deux "
        "droits.",
        "Ne s'applique pas aux clients revenant apres une periode "
        "d'inactivite.",
    ]:
        doc.add_paragraph(point, style="List Bullet")

    doc.add_heading("7. Gestes commerciaux exceptionnels", level=1)
    for point in [
        "Motif obligatoire, texte libre de 20 caracteres minimum.",
        "Soumis aux seuils de delegation de la section 4 et au plafond de "
        "15 %.",
        "Trace nominativement : demandeur, validateur, motif, horodatage.",
        "Volume surveille : le controle de gestion recoit un etat mensuel.",
    ]:
        doc.add_paragraph(point, style="List Bullet")

    doc.add_heading("8. Ce que les regles ne couvrent pas", level=1)
    doc.add_paragraph(
        "La consolidation du volume entre plusieurs sites d'un meme groupe "
        "n'est pas prevue a ce jour. Chaque entite facturee est traitee "
        "independamment."
    )
    doc.add_paragraph(
        "Les remises sur recurrence de commande n'existent pas dans le "
        "bareme en vigueur. Aucun taux, aucune periodicite n'a ete arrete."
    )

    chemin = ICI / "specification-remises.docx"
    doc.save(chemin)
    return chemin


def generer_pptx() -> Path:
    """Le deck du comite tarifaire : peu de texte, beaucoup de balisage."""
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    props = prs.core_properties
    props.author = "Direction commerciale NovaTech Industries"
    props.title = "Comite tarifaire - arbitrages remises 2.4"

    # Titre
    s = prs.slides.add_slide(prs.slide_layouts[0])
    s.shapes.title.text = "Comite tarifaire du 28 aout 2026"
    s.placeholders[1].text = (
        "Arbitrages sur les regles de remise - version 2.4\n"
        "Applicable au 1er septembre 2026"
    )

    contenus = [
        (
            "Ce qui a ete arbitre",
            [
                "Plafond de remise maintenu a 15 %, sans exception",
                "Non-cumul des remises confirme comme regle absolue",
                "Seuils de delegation revus : 8 % / 12 % / 15 %",
                "Validation du directeur commercial rendue nominative",
                "Fichier Excel du service commercial proscrit",
            ],
        ),
        (
            "Bareme par volume",
            [
                "Moins de 1 000 EUR : aucune remise",
                "De 1 000 a 5 000 EUR : 3 %",
                "De 5 000 a 20 000 EUR : 6 %",
                "A partir de 20 000 EUR : 9 %",
                "Bonus categorie : Privilege +2 pts, Grand compte +4 pts",
            ],
        ),
        (
            "Points non tranches",
            [
                "Consolidation du volume entre sites d'un meme groupe : a "
                "l'etude, sans decision",
                "Remises sur recurrence de commande : aucun taux arrete",
                "Revue du bareme au 1er trimestre 2027",
            ],
        ),
        (
            "Impacts applicatifs attendus",
            [
                "Le calculateur doit rejeter, pas signaler",
                "Tracabilite nominative des gestes commerciaux",
                "Etat mensuel au controle de gestion",
                "Mise en production visee : 1er septembre 2026",
            ],
        ),
    ]

    for titre, points in contenus:
        s = prs.slides.add_slide(prs.slide_layouts[1])
        s.shapes.title.text = titre
        tf = s.placeholders[1].text_frame
        tf.clear()
        for i, point in enumerate(points):
            p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
            p.text = point
            p.font.size = PptPt(20)

    chemin = ICI / "comite-tarifaire.pptx"
    prs.save(chemin)
    return chemin


def generer_xlsx() -> Path:
    """Le bareme en tableur : la mise en forme pese plus que les donnees."""
    wb = Workbook()

    entete_fond = PatternFill("solid", fgColor="1F4E79")
    entete_police = Font(color="FFFFFF", bold=True, size=12)
    centre = Alignment(horizontal="center", vertical="center")

    ws = wb.active
    ws.title = "Bareme volume"
    ws.append(["Montant HT min", "Montant HT max", "Taux", "Commentaire"])
    for cell in ws[1]:
        cell.fill = entete_fond
        cell.font = entete_police
        cell.alignment = centre
    for ligne in [
        [0, 1000, 0.00, "Aucune remise sous 1 000 EUR"],
        [1000, 5000, 0.03, "Premier palier"],
        [5000, 20000, 0.06, "Deuxieme palier"],
        [20000, None, 0.09, "Palier maximal du bareme"],
    ]:
        ws.append(ligne)
    for col, largeur in zip("ABCD", [16, 16, 10, 40]):
        ws.column_dimensions[col].width = largeur
    for ligne in ws.iter_rows(min_row=2, min_col=3, max_col=3):
        for cell in ligne:
            cell.number_format = "0.00%"

    ws2 = wb.create_sheet("Bonus categorie")
    ws2.append(["Categorie", "Bonus (points)", "Commentaire"])
    for cell in ws2[1]:
        cell.fill = entete_fond
        cell.font = entete_police
        cell.alignment = centre
    for ligne in [
        ["STANDARD", 0, "Categorie par defaut"],
        ["PRIVILEGE", 2, "Clients recurrents"],
        ["GRAND_COMPTE", 4, "Sous contrat cadre"],
    ]:
        ws2.append(ligne)
    for col, largeur in zip("ABC", [18, 16, 34]):
        ws2.column_dimensions[col].width = largeur

    ws3 = wb.create_sheet("Delegation")
    ws3.append(["Taux min", "Taux max", "Validateur"])
    for cell in ws3[1]:
        cell.fill = entete_fond
        cell.font = entete_police
        cell.alignment = centre
    for ligne in [
        [0.00, 0.08, "Automatique"],
        [0.08, 0.12, "Responsable commercial"],
        [0.12, 0.15, "Directeur commercial"],
        [0.15, None, "Interdit"],
    ]:
        ws3.append(ligne)
    for col, largeur in zip("ABC", [12, 12, 28]):
        ws3.column_dimensions[col].width = largeur

    chemin = ICI / "bareme-remises.xlsx"
    wb.save(chemin)
    return chemin


if __name__ == "__main__":
    for chemin in (generer_docx(), generer_pptx(), generer_xlsx()):
        print(f"{chemin.name:32s} {chemin.stat().st_size:>8d} octets")
