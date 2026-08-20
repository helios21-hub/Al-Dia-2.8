package com.aldia.app;

import android.content.Context;
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
        String local = record.optString("local", "953");
        String articulo = record.optString("articulo", "");
        String descripcion = record.optString("descripcion", "");
        String vencimiento = formatDate(record.optString("expiryDate", ""));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", sheet(local, articulo, descripcion, vencimiento));
        }
        return bytes.toByteArray();
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
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"+
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFB7E5A4\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"+
                "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"><color rgb=\"FF777777\"/></left><right style=\"thin\"><color rgb=\"FF777777\"/></right><top style=\"thin\"><color rgb=\"FF777777\"/></top><bottom style=\"thin\"><color rgb=\"FF777777\"/></bottom><diagonal/></border></borders>"+
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"+
                "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"+
                "</styleSheet>";
    }

    private static String sheet(String local, String articulo, String descripcion, String vencimiento) {
        String[] headers={"Local","Articulo","Descripcion","Accion","Vigencia desde","Vigencia hasta","Fecha de vencimiento del Producto"};
        String[] values={local,articulo,descripcion,"","","",vencimiento};
        StringBuilder h=new StringBuilder(),v=new StringBuilder();
        for(int i=0;i<headers.length;i++){String col=String.valueOf((char)('A'+i));h.append(cell(col+"1",headers[i],1));v.append(cell(col+"2",values[i],2));}
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+
                "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"+
                "<cols><col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"18\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"32\" customWidth=\"1\"/><col min=\"4\" max=\"6\" width=\"18\" customWidth=\"1\"/><col min=\"7\" max=\"7\" width=\"32\" customWidth=\"1\"/></cols>"+
                "<sheetData><row r=\"1\" ht=\"34\" customHeight=\"1\">"+h+"</row><row r=\"2\" ht=\"24\" customHeight=\"1\">"+v+"</row></sheetData>"+
                "<autoFilter ref=\"A1:G2\"/></worksheet>";
    }

    private static String cell(String ref, String text, int style) {
        return "<c r=\""+ref+"\" t=\"inlineStr\" s=\""+style+"\"><is><t xml:space=\"preserve\">"+xml(text)+"</t></is></c>";
    }
    private static String xml(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String safeId(String s){String v=(s==null?"":s).replaceAll("[^A-Za-z0-9_-]","_");return v.isEmpty()?String.valueOf(System.currentTimeMillis()):v;}
    private static String formatDate(String iso){try{return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}catch(Exception e){return iso==null?"":iso;}}
}
