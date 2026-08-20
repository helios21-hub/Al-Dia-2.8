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

public final class CarnesVegetalesXlsx {
    private CarnesVegetalesXlsx() {}

    public static String defaultFileName() {
        return "Plantilla Carnes-Vegetales " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy")) + ".xlsx";
    }

    public static File writePrivate(Context context, JSONObject record) throws Exception {
        File dir = new File(context.getFilesDir(), "xlsx");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("No se pudo crear la carpeta XLSX");
        String id = safeId(record.optString("id", String.valueOf(System.currentTimeMillis())));
        File file = new File(dir, id + ".xlsx");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(create(record)); }
        return file;
    }

    public static byte[] create(JSONObject record) throws Exception {
        JSONArray items = normalizedItems(record);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", sheet(items));
        }
        return bytes.toByteArray();
    }

    private static JSONArray normalizedItems(JSONObject record) {
        JSONArray items = record.optJSONArray("items");
        if (items != null && items.length() > 0) return items;
        JSONArray fallback = new JSONArray();
        if (record.has("articulo") || record.has("descripcion") || record.has("expiryDate") || record.has("expiryText")) {
            JSONObject item = new JSONObject();
            try {
                item.put("local", record.optString("local", "953"));
                item.put("articulo", record.optString("articulo", ""));
                item.put("descripcion", record.optString("descripcion", ""));
                item.put("expiryText", record.optString("expiryText", record.optString("expiryDate", "")));
                fallback.put(item);
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name); zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"+
                "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"+
                "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"+
                "<sheets><sheet name=\"Carnes-Vegetales\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"+
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"+
                "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Aptos Narrow\"/></font><font><b/><sz val=\"11\"/><name val=\"Aptos Narrow\"/></font></fonts>"+
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFB8DCAB\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"+
                "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"><color indexed=\"64\"/></left><right style=\"thin\"><color indexed=\"64\"/></right><top style=\"thin\"><color indexed=\"64\"/></top><bottom style=\"thin\"><color indexed=\"64\"/></bottom><diagonal/></border></borders>"+
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"+
                "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf></cellXfs>"+
                "</styleSheet>";
    }

    private static String sheet(JSONArray items) {
        String[] headers={"Local","Artiulo","Descripcion","Accion","Vigencia desde","Vigencia hasta","Fecha de vencimiento del Producto"};
        String[] subtitles={"(Lo completa el local)","(Lo completa el local)","(Lo completa el local)","(Lo completa compras)","(Lo completa compras)","(Lo completa compras)","(completa el local)"};
        boolean[] localFields={true,true,true,false,false,false,true};
        StringBuilder h=new StringBuilder(),rows=new StringBuilder();
        for(int i=0;i<headers.length;i++){
            String col=String.valueOf((char)('A'+i));
            h.append(headerCell(col+"1",headers[i],subtitles[i],localFields[i]));
        }
        for(int r=0;r<items.length();r++){
            JSONObject item=items.optJSONObject(r); if(item==null)item=new JSONObject();
            int row=r+2;
            String[] values={item.optString("local","953"),item.optString("articulo",""),item.optString("descripcion",""),"","","",item.optString("expiryText",item.optString("expiryDate",""))};
            rows.append("<row r=\"").append(row).append("\" ht=\"18\" customHeight=\"1\">");
            for(int i=0;i<values.length;i++){
                String col=String.valueOf((char)('A'+i));
                rows.append(dataCell(col+row,values[i]));
            }
            rows.append("</row>");
        }
        int lastRow=Math.max(1,items.length()+1);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+
                "<dimension ref=\"A1:G"+lastRow+"\"/>"+
                "<cols><col min=\"1\" max=\"1\" width=\"21\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"26.71\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"25.86\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"20\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"25.43\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"22.43\" customWidth=\"1\"/><col min=\"7\" max=\"7\" width=\"33.57\" customWidth=\"1\"/></cols>"+
                "<sheetData><row r=\"1\" ht=\"45\" customHeight=\"1\">"+h+"</row>"+rows+"</sheetData>"+
                "<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>"+
                "</worksheet>";
    }

    private static String headerCell(String ref,String title,String subtitle,boolean localField){
        String color=localField?"FFFF0000":"FF0B76A0";
        return "<c r=\""+ref+"\" t=\"inlineStr\" s=\"1\"><is>"+
                "<r><rPr><b/><sz val=\"11\"/><color rgb=\"FF000000\"/><rFont val=\"Aptos Narrow\"/></rPr><t xml:space=\"preserve\">"+xml(title)+"&#10;</t></r>"+
                "<r><rPr><b/><sz val=\"11\"/><color rgb=\""+color+"\"/><rFont val=\"Aptos Narrow\"/></rPr><t>"+xml(subtitle)+"</t></r>"+
                "</is></c>";
    }
    private static String dataCell(String ref,String text){
        if(text==null||text.isEmpty())return "<c r=\""+ref+"\" s=\"2\"/>";
        return "<c r=\""+ref+"\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">"+xml(text)+"</t></is></c>";
    }
    private static String xml(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String safeId(String s){String v=(s==null?"":s).replaceAll("[^A-Za-z0-9_-]","_");return v.isEmpty()?String.valueOf(System.currentTimeMillis()):v;}
}
