package com.aldia.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportador autónomo de la Lista de códigos.
 * Mantiene la estructura visual de la planilla original: 8 hojas, cuatro columnas,
 * encabezados en negrita, bordes y anchos equivalentes. Los códigos se escriben
 * como texto para evitar notación científica o pérdida de ceros iniciales.
 */
public final class ListaCodigosXlsx {
    private ListaCodigosXlsx() {}

    public static String defaultFileName() {
        return "Planilla Lista de Codigos-" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".xlsx";
    }

    public static File writePrivate(Context context, JSONObject root) throws Exception {
        File dir = new File(context.getFilesDir(), "xlsx");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("No se pudo crear la carpeta XLSX");
        String name = safeName(root.optString("fileName", defaultFileName()));
        File file = new File(dir, System.currentTimeMillis() + "-" + name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(create(root));
        }
        return file;
    }

    public static byte[] create(JSONObject root) throws Exception {
        JSONArray sheets = root == null ? null : root.optJSONArray("sheets");
        if (sheets == null) sheets = new JSONArray();
        int count = Math.max(1, sheets.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", contentTypes(count));
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook(sheets, count));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(count));
            put(zip, "xl/styles.xml", styles());
            for (int i = 0; i < count; i++) {
                JSONObject sheet = i < sheets.length() ? sheets.optJSONObject(i) : null;
                JSONArray rows = sheet == null ? null : sheet.optJSONArray("rows");
                if (rows == null) rows = new JSONArray();
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", worksheet(rows));
            }
        }
        return bytes.toByteArray();
    }

    private static String contentTypes(int count) {
        StringBuilder overrides = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            overrides.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                overrides + "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbook(JSONArray sheets, int count) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < count; i++) {
            JSONObject sheet = i < sheets.length() ? sheets.optJSONObject(i) : null;
            String name = sheet == null ? "Lista " + (i + 1) : sheet.optString("name", "Lista " + (i + 1));
            if (name.trim().isEmpty()) name = "Lista " + (i + 1);
            out.append("<sheet name=\"").append(xml(name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        out.append("</sheets></workbook>");
        return out.toString();
    }

    private static String workbookRels(int count) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= count; i++) {
            out.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        out.append("<Relationship Id=\"rId").append(count + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                .append("</Relationships>");
        return out.toString();
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><color rgb=\"FF000000\"/><name val=\"Arial\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FF000000\"/><name val=\"Arial\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FF000000\"/></left><right style=\"thin\"><color rgb=\"FF000000\"/></right><top style=\"thin\"><color rgb=\"FF000000\"/></top><bottom style=\"thin\"><color rgb=\"FF000000\"/></bottom></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"5\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>" +
                "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }

    private static String worksheet(JSONArray rows) {
        StringBuilder data = new StringBuilder();
        data.append("<row r=\"1\">")
                .append(cell("A1", "articuloref", 1))
                .append(cell("B1", "Scanning", 1))
                .append(cell("C1", "Descripción", 1))
                .append(cell("D1", "packaging", 1))
                .append("</row>");
        int last = 1;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            int r = last + 1;
            String a = clean(row.optString("articuloref", ""));
            String b = clean(row.optString("scanning", row.optString("Scanning", "")));
            String c = clean(row.optString("descripcion", row.optString("Descripción", "")));
            String d = clean(row.optString("packaging", ""));
            if (a.isEmpty() && b.isEmpty() && c.isEmpty() && d.isEmpty()) continue;
            data.append("<row r=\"").append(r).append("\">")
                    .append(cell("A" + r, a, 2))
                    .append(cell("B" + r, b, 3))
                    .append(cell("C" + r, c, 4))
                    .append(cell("D" + r, d, 4))
                    .append("</row>");
            last = r;
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<dimension ref=\"A1:D" + last + "\"/>" +
                "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>" +
                "<sheetFormatPr defaultRowHeight=\"15.75\"/>" +
                "<cols><col customWidth=\"1\" min=\"1\" max=\"1\" width=\"12.5\"/><col customWidth=\"1\" min=\"2\" max=\"2\" width=\"11.88\"/><col customWidth=\"1\" min=\"3\" max=\"3\" width=\"55\"/><col customWidth=\"1\" min=\"4\" max=\"4\" width=\"12.38\"/></cols>" +
                "<sheetData>" + data + "</sheetData>" +
                "<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>" +
                "</worksheet>";
    }

    private static String cell(String ref, String value, int style) {
        if (value == null || value.isEmpty()) return "<c r=\"" + ref + "\" s=\"" + style + "\"/>";
        return "<c r=\"" + ref + "\" t=\"inlineStr\" s=\"" + style + "\"><is><t xml:space=\"preserve\">" + xml(value) + "</t></is></c>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeName(String s) {
        String name = (s == null || s.trim().isEmpty()) ? defaultFileName() : s.trim();
        return name.replace("/", "-").replace("\\", "-");
    }

    private static String xml(String s) {
        return (s == null ? "" : s)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
