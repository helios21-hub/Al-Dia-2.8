package com.aldia.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int PICK_BACKUP_REQUEST = 2001;
    private static final int SAVE_BACKUP_REQUEST = 2002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2003;
    private static final int SAVE_XLSX_REQUEST = 2004;
    public static final String CHANNEL_ID = "al_dia_alertas";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingBackupContent;
    private byte[] pendingXlsxBytes;
    private String pendingXlsxName;
    private boolean pendingTestNotification = false;
    private boolean pendingProductTestNotification = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                checkNotificationPermissionOnStart();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), 1001);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidNative");
        webView.loadUrl("file:///android_asset/index.html");

        if (getSharedPreferences("al_dia", MODE_PRIVATE).getBoolean("notifications_enabled", true)) {
            NotificationScheduler.scheduleAll(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getSharedPreferences("al_dia", MODE_PRIVATE).getBoolean("notifications_enabled", true)) {
            NotificationScheduler.scheduleAll(this);
        }
        notifyPermissionStateToWeb();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Alertas de Al Día", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Avisos de vencimientos y recordatorios");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveBackup(String content) { runOnUiThread(() -> chooseBackupDestination(content)); }

        @JavascriptInterface
        public void exportBackup(String content) { runOnUiThread(() -> chooseBackupDestination(content)); }

        @JavascriptInterface
        public void chooseBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, PICK_BACKUP_REQUEST);
            });
        }

        @JavascriptInterface
        public void persistAppState(String content) {
            getSharedPreferences("al_dia_state", MODE_PRIVATE).edit().putString("state_json", content == null ? "" : content).apply();
        }

        @JavascriptInterface
        public String getPersistedAppState() {
            return getSharedPreferences("al_dia_state", MODE_PRIVATE).getString("state_json", "");
        }

        @JavascriptInterface
        public void updateNotificationSettings(boolean enabled, String dataJson, int hour, int minute, int repeatMinutes) {
            runOnUiThread(() -> {
                SharedPreferences prefs = getSharedPreferences("al_dia", MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("notifications_enabled", enabled)
                        .putString("notification_data", dataJson == null ? "{}" : dataJson)
                        .putInt("notification_hour", Math.max(0, Math.min(23, hour)))
                        .putInt("notification_minute", Math.max(0, Math.min(59, minute)))
                        .putInt("notification_repeat_minutes", (repeatMinutes == 30 || repeatMinutes == 60 || repeatMinutes == 120) ? repeatMinutes : 0)
                        .apply();
                NotificationScheduler.cancelAll(MainActivity.this);
                if (enabled) {
                    checkNotificationPermissionOnStart();
                    NotificationScheduler.scheduleAll(MainActivity.this);
                }
            });
        }

        @JavascriptInterface
        public String getNotificationPermissionStatus() {
            if (Build.VERSION.SDK_INT < 33) return "not_required";
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
        }

        @JavascriptInterface
        public void requestNotificationPermissionManually() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT < 33) return;
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notifyPermissionStateToWeb();
                    return;
                }
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                        || !getSharedPreferences("al_dia", MODE_PRIVATE).getBoolean("permission_prompted", false)) {
                    markPermissionPrompted();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                } else {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(i);
                }
            });
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Al Día" : title);
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(send, "Compartir con"));
            });
        }

        @JavascriptInterface
        public void testNotification() {
            runOnUiThread(() -> {
                if (!ensureNotificationPermissionForTest()) return;
                sendGenericTestNotification();
            });
        }

        @JavascriptInterface
        public void testProductNotification() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    pendingProductTestNotification = true;
                    requestNotificationPermissionManually();
                    return;
                }
                NotificationScheduler.triggerProductTest(MainActivity.this);
            });
        }

        @JavascriptInterface
        public String getAppVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "2.18";
            }
        }

        @JavascriptInterface
        public boolean createCarnesVegetalesXlsx(String recordJson) {
            try {
                JSONObject record = new JSONObject(recordJson == null ? "{}" : recordJson);
                File file = CarnesVegetalesXlsx.writePrivate(MainActivity.this, record);
                return file.exists() && file.length() > 0;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void exportCarnesVegetalesXlsx(String recordJson) {
            runOnUiThread(() -> {
                try {
                    JSONObject record = new JSONObject(recordJson == null ? "{}" : recordJson);
                    pendingXlsxBytes = CarnesVegetalesXlsx.create(record);
                    pendingXlsxName = record.optString("fileName", CarnesVegetalesXlsx.defaultFileName());
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingXlsxName);
                    startActivityForResult(intent, SAVE_XLSX_REQUEST);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo preparar el XLSX", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareCarnesVegetalesXlsx(String recordJson) {
            runOnUiThread(() -> {
                try {
                    JSONObject record = new JSONObject(recordJson == null ? "{}" : recordJson);
                    File file = CarnesVegetalesXlsx.writePrivate(MainActivity.this, record);
                    String displayName = record.optString("fileName", CarnesVegetalesXlsx.defaultFileName());
                    Uri uri = new Uri.Builder()
                            .scheme("content")
                            .authority(getPackageName() + ".files")
                            .appendPath("xlsx")
                            .appendPath(file.getName())
                            .appendQueryParameter("name", displayName)
                            .build();
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(ClipData.newRawUri(displayName, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Compartir XLSX"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo compartir el XLSX", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void startBarcodeScan() {
            runOnUiThread(MainActivity.this::launchBarcodeScanner);
        }

        @JavascriptInterface
        public void vibrate(int milliseconds) {
            int duration = Math.max(10, Math.min(300, milliseconds));
            runOnUiThread(() -> {
                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator == null || !vibrator.hasVibrator()) return;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(duration);
                    }
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void checkForUpdates() {
            new Thread(MainActivity.this::performUpdateCheck).start();
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "No se pudo abrir el enlace", Toast.LENGTH_LONG).show(); }
            });
        }
    }

    private void checkNotificationPermissionOnStart() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences("al_dia", MODE_PRIVATE);
        if (!p.getBoolean("notifications_enabled", true)) return;
        if (!p.getBoolean("permission_prompted", false)) {
            markPermissionPrompted();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void markPermissionPrompted() {
        getSharedPreferences("al_dia", MODE_PRIVATE).edit().putBoolean("permission_prompted", true).apply();
    }

    private boolean ensureNotificationPermissionForTest() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true;
        pendingTestNotification = true;
        new AndroidBridge().requestNotificationPermissionManually();
        return false;
    }

    private void notifyPermissionStateToWeb() {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript("window.onNotificationPermissionChanged && window.onNotificationPermissionChanged();", null));
    }

    private void sendGenericTestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        PendingIntent contentIntent = appPendingIntent(4401);
        String message = "Prueba correcta. Al Día puede mostrar notificaciones.";
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Al Día · Notificación de prueba")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true);
        if (contentIntent != null) b.setContentIntent(contentIntent);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(4402, b.build());
    }

    private PendingIntent appPendingIntent(int code) {
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        return open == null ? null : PendingIntent.getActivity(this, code, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void launchBarcodeScanner() {
        try {
            sendBarcodeScanStatus("opening", "Abriendo escáner…");
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .enableAutoZoom()
                    .build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String code = barcode == null ? "" : barcode.getRawValue();
                        if (code == null || code.trim().isEmpty()) {
                            sendBarcodeScanStatus("error", "No se pudo leer el código. Intentá nuevamente.");
                            return;
                        }
                        final String cleanCode = code.trim();
                        sendBarcodeScanResult(cleanCode, "", "barcode");
                        sendBarcodeScanStatus("looking_up", "Código leído. Buscando el nombre del producto…");
                        new Thread(() -> lookupBarcodeAndReturn(cleanCode)).start();
                    })
                    .addOnCanceledListener(() -> sendBarcodeScanStatus("canceled", "Escaneo cancelado."))
                    .addOnFailureListener(e -> sendBarcodeScanStatus("error", "No se pudo abrir el escáner. Verificá Google Play Services e intentá nuevamente."));
        } catch (Exception e) {
            sendBarcodeScanStatus("error", "No se pudo iniciar el escáner de código de barras.");
        }
    }

    private static class BarcodeLookupResult {
        String name = "";
        String source = "barcode";
    }

    private void lookupBarcodeAndReturn(String code) {
        BarcodeLookupResult result = lookupBarcodeDomain("https://world.openfoodfacts.org", code, "Open Food Facts");
        if (result.name.isEmpty()) {
            BarcodeLookupResult fallback = lookupBarcodeDomain("https://world.openproductsfacts.org", code, "Open Products Facts");
            if (!fallback.name.isEmpty()) result = fallback;
        }
        sendBarcodeLookupResult(code, result.name, result.source);
    }

    private BarcodeLookupResult lookupBarcodeDomain(String domain, String code, String sourceName) {
        BarcodeLookupResult result = new BarcodeLookupResult();
        HttpURLConnection c = null;
        try {
            URL url = new URL(domain + "/api/v2/product/" + code + ".json?fields=product_name,product_name_es,brands,quantity");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "AlDia/2.18 Android barcode lookup");
            int response = c.getResponseCode();
            if (response != 200) return result;
            String body;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                body = sb.toString();
            }
            JSONObject root = new JSONObject(body);
            if (root.optInt("status", 0) != 1) return result;
            JSONObject product = root.optJSONObject("product");
            if (product == null) return result;
            String productName = product.optString("product_name_es", "").trim();
            if (productName.isEmpty()) productName = product.optString("product_name", "").trim();
            String brands = product.optString("brands", "").trim();
            String quantity = product.optString("quantity", "").trim();
            result.name = composeBarcodeProductName(productName, brands, quantity);
            if (!result.name.isEmpty()) result.source = sourceName;
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return result;
    }

    private String composeBarcodeProductName(String productName, String brands, String quantity) {
        String name = productName == null ? "" : productName.trim();
        String brand = brands == null ? "" : brands.split(",")[0].trim();
        String qty = quantity == null ? "" : quantity.trim();
        StringBuilder out = new StringBuilder();
        String lowerName = name.toLowerCase();
        String lowerBrand = brand.toLowerCase();
        if (!brand.isEmpty() && (name.isEmpty() || !lowerName.contains(lowerBrand))) out.append(brand);
        if (!name.isEmpty()) {
            if (out.length() > 0) out.append(" ");
            out.append(name);
        }
        String current = out.toString().toLowerCase();
        if (!qty.isEmpty() && !current.contains(qty.toLowerCase())) {
            if (out.length() > 0) out.append(" ");
            out.append(qty);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private void sendBarcodeScanStatus(String status, String message) {
        if (webView == null) return;
        String js = "window.onBarcodeScanStatus && window.onBarcodeScanStatus(" + JSONObject.quote(status) + "," + JSONObject.quote(message) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendBarcodeScanResult(String code, String name, String source) {
        if (webView == null) return;
        String js = "window.onBarcodeScanResult && window.onBarcodeScanResult(" + JSONObject.quote(code) + "," + JSONObject.quote(name == null ? "" : name) + "," + JSONObject.quote(source == null ? "barcode" : source) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendBarcodeLookupResult(String code, String name, String source) {
        if (webView == null) return;
        String js = "window.onBarcodeLookupResult && window.onBarcodeLookupResult(" + JSONObject.quote(code) + "," + JSONObject.quote(name == null ? "" : name) + "," + JSONObject.quote(source == null ? "barcode" : source) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void performUpdateCheck() {
        String repo = BuildConfig.GITHUB_REPOSITORY;
        if (repo == null || repo.trim().isEmpty()) {
            sendUpdateResult("not_configured", "", "");
            return;
        }
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "Al-Dia-Android");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            String body;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                body = sb.toString();
            }
            JSONObject o = new JSONObject(body);
            String tag = o.optString("tag_name", "").replaceFirst("^[vV]", "");
            String page = o.optString("html_url", "https://github.com/" + repo + "/releases/latest");
            String current = new AndroidBridge().getAppVersion();
            sendUpdateResult(compareVersions(tag, current) > 0 ? "available" : "up_to_date", tag.isEmpty()?current:tag, page);
        } catch (Exception e) {
            sendUpdateResult("error", "", "https://github.com/" + repo + "/releases");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private int compareVersions(String a, String b) {
        String[] aa=(a==null?"":a).split("\\."), bb=(b==null?"":b).split("\\.");
        int n=Math.max(aa.length,bb.length);
        for(int i=0;i<n;i++){
            int x=parseVersionPart(i<aa.length?aa[i]:"0"), y=parseVersionPart(i<bb.length?bb[i]:"0");
            if(x!=y)return Integer.compare(x,y);
        }
        return 0;
    }

    private int parseVersionPart(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9].*$","")); }
        catch(Exception e){ return 0; }
    }

    private void sendUpdateResult(String status, String version, String url) {
        if (webView == null) return;
        String js="window.onUpdateCheckResult && window.onUpdateCheckResult("
                + JSONObject.quote(status)+","+JSONObject.quote(version)+","+JSONObject.quote(url)+");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted=grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED;
            if (granted) {
                NotificationScheduler.scheduleAll(this);
                if (pendingTestNotification) sendGenericTestNotification();
                if (pendingProductTestNotification) NotificationScheduler.triggerProductTest(this);
            }
            pendingTestNotification=false;
            pendingProductTestNotification=false;
            notifyPermissionStateToWeb();
        }
    }

    private void chooseBackupDestination(String content) {
        pendingBackupContent = content;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "copia-al-dia-" + System.currentTimeMillis() + ".json");
        startActivityForResult(intent, SAVE_BACKUP_REQUEST);
    }

    private void writeBackup(Uri uri, String content) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new Exception("No se pudo abrir el archivo");
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String quoteForJavaScript(String text) {
        return "'" + text.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n")
                .replace("\u2028","\\u2028").replace("\u2029","\\u2029") + "'";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 1001) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
            filePathCallback.onReceiveValue(results); filePathCallback=null; return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri=data.getData();
        try {
            if (requestCode == PICK_BACKUP_REQUEST) {
                String text=readText(uri);
                webView.evaluateJavascript("window.receiveBackupFromAndroid && window.receiveBackupFromAndroid("+quoteForJavaScript(text)+");",null);
            } else if (requestCode == SAVE_BACKUP_REQUEST) {
                writeBackup(uri,pendingBackupContent);
                Toast.makeText(this,"Copia exportada correctamente",Toast.LENGTH_LONG).show();
                webView.evaluateJavascript("window.onBackupExported && window.onBackupExported();",null);
            } else if (requestCode == SAVE_XLSX_REQUEST) {
                if (pendingXlsxBytes == null) throw new Exception("No hay XLSX pendiente");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("No se pudo abrir el archivo");
                    out.write(pendingXlsxBytes);
                    out.flush();
                }
                Toast.makeText(this,"XLSX guardado correctamente",Toast.LENGTH_LONG).show();
                pendingXlsxBytes = null;
                pendingXlsxName = null;
            }
        } catch(Exception e) {
            Toast.makeText(this,"No se pudo completar la operación",Toast.LENGTH_LONG).show();
        }
    }
}
