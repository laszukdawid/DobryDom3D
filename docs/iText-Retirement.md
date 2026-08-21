# iText 2.1.7 Retirement Plan

iText 2.1.7 (2009, end-of-life, CVE-era XXE exposure — CVE-2017-9096 family)
ships in every distributed artifact except macOS packages. This page records
the investigation and the replacement plan.

## Current usage surface

The dependency has exactly **one consumer**:
`swing/HomePDFPrinter.java` (used by `HomePane` for the
"Export to PDF" action). Its full API touchpoints:

| Import (`com.lowagie.*`) | Used for |
|---|---|
| `Document` | PDF document lifecycle: `open`, `close`, `newPage`, metadata setters (`addAuthor/addCreator/addCreationDate/addTitle`) |
| `Rectangle` | Page size from the AWT `PageFormat` |
| `DocumentException` | Error handling |
| `PdfWriter.getInstance` | Binding document to output stream |
| `PdfContentByte` | `createTemplate(...)`, `addTemplate(...)` per printed page |
| `PdfTemplate` | `createGraphicsShapes(...)` → draws each Swing-printed page into the PDF |

No other source file references iText classes; no fonts, forms, encryption,
or HTML features are used. The integration is a thin "Swing printable → PDF"
bridge (~80 lines).

Packaging notes:

- macOS packages already delete the jar (`_prepareMacPackage`,
  build.xml) because macOS offers PDF output from its print dialog.
- The jar is also repackaged into `deploy/lib/` by the dead Web Start targets.
- License file: `THIRDPARTY-LICENSE-ITEXT.TXT`.

## Replacement options investigated

### Option A (chosen): OpenPDF

OpenPDF is the maintained fork of iText 4.x's last LGPL/MPL-dual-licensed
lineage (Maven coordinates `com.github.librepdf:openpdf`). License is
LGPL-2.1 / MPL-2.0 dual — compatible with this GPLv2-or-later project.

Two migration paths verified against published artifacts:

1. **Drop-in swap** — OpenPDF **≤ 2.x keeps the `com.lowagie` package
   namespace**. Verified: `openpdf-1.4.2.jar` contains
   `com/lowagie/text/Document.class` and
   `com/lowagie/text/pdf/PdfTemplate.class`. Replacing
   `lib/iText-2.1.7.jar` with `lib/openpdf-1.4.2.jar`
   (SHA-256 `4adf14d510007161ce09815fe635f90342ac28eb5f5b7517954002877cf09f02`)
   requires zero source changes.
2. **Namespace migration** — OpenPDF **3.x renames `com.lowagie` →
   `org.openpdf`**. Verified present in `openpdf-3.0.5.jar`
   (SHA-256 `2a70c6024bb10b6192b4b91d8bb581de24b776a799a30ef5b87748e54aca2144`).
   Requires changing exactly 6 import lines in `HomePDFPrinter.java`.

Recommended path: land option 1 first (instant risk removal), then take the
mechanical import migration to 3.x as a follow-up so we are not pinned to a
legacy line.

### Options rejected

- **iText 5+/7+** — AGPL; incompatible with a GPL desktop app unless the whole
  app relicenses or a commercial license is bought.
- **JavaFX/PDFBox rendering bridges** — Apache PDFBox cannot consume an
  arbitrary AWT `Graphics2D` target; would require rewriting the export path,
  not swapping the backend.
- **Removing PDF export entirely** (as macOS already effectively does) —
  Windows/Linux users lose a documented feature.

## Execution steps

1. Replace `lib/iText-2.1.7.jar` with `lib/openpdf-1.4.2.jar` (checksum above;
   use the `download-verified` pattern if fetched instead of committed).
2. Update build.xml references: application classpath globs pick it up
   automatically, but update the literal names in `_prepareMacPackage`
   (delete line) and the Web Start repackaging block (or drop that block with
   the legacy-targets cleanup).
3. Compile and run `ant test` plus `PrintTest` locally; manually export one
   multi-page home to PDF on Linux and open it in two viewers.
4. Swap `THIRDPARTY-LICENSE-ITEXT.TXT` for the OpenPDF notice (LGPL-2.1 /
   MPL-2.0), keeping upstream attribution in CREDITS.md.
5. Follow-up PR: migrate `HomePDFPrinter.java` imports to `org.openpdf.*`,
   bump the jar to OpenPDF 3.x, re-verify.
