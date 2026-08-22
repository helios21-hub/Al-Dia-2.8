package com.aldia.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
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

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_BACKUP_REQUEST = 2001;
    private static final int SAVE_BACKUP_REQUEST = 2002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2003;
    private static final int SAVE_XLSX_REQUEST = 2004;
    private static final int PHOTO_CAPTURE_REQUEST = 2005;
    private static final int DOCUMENT_SCAN_REQUEST = 2006;
    public static final String CHANNEL_ID = "al_dia_alertas";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingBackupContent;
    private byte[] pendingXlsxBytes;
    private String pendingXlsxName;
    private File pendingPhotoFile;
    private String pendingPhotoPurpose = "";
    private boolean pendingTestNotification = false;
    private boolean pendingProductTestNotification = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        cleanupCaptureCache();

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

    private void cleanupCaptureCache() {
        try {
            File dir = new File(getCacheDir(), "capture");
            File[] files = dir.listFiles();
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            for (File f : files) if (f.isFile() && f.lastModified() < cutoff) try { f.delete(); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
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
                return "2.21";
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
            runOnUiThread(() -> launchBarcodeScanner("replenishment"));
        }

        @JavascriptInterface
        public void startBarcodeScanFor(String purpose) {
            final String safePurpose = purpose == null || purpose.trim().isEmpty() ? "replenishment" : purpose.trim();
            runOnUiThread(() -> launchBarcodeScanner(safePurpose));
        }

        @JavascriptInterface
        public void captureTextPhoto(String purpose) {
            final String safePurpose = purpose == null ? "" : purpose.trim();
            runOnUiThread(() -> launchTextPhoto(safePurpose));
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

    private void launchBarcodeScanner(String purpose) {
        final String scanPurpose = purpose == null || purpose.trim().isEmpty() ? "replenishment" : purpose.trim();
        try {
            sendBarcodeScanStatus(scanPurpose, "opening", "Abriendo escáner…");
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .enableAutoZoom()
                    .build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String code = barcode == null ? "" : barcode.getRawValue();
                        if (code == null || code.trim().isEmpty()) {
                            sendBarcodeScanStatus(scanPurpose, "error", "No se pudo leer el código. Intentá nuevamente.");
                            return;
                        }
                        final String cleanCode = code.trim();
                        sendBarcodeScanResult(scanPurpose, cleanCode, "", "barcode");
                        sendBarcodeScanStatus(scanPurpose, "looking_up", "Código leído. Buscando el nombre del producto…");
                        new Thread(() -> lookupBarcodeAndReturn(cleanCode, scanPurpose)).start();
                    })
                    .addOnCanceledListener(() -> sendBarcodeScanStatus(scanPurpose, "canceled", "Escaneo cancelado."))
                    .addOnFailureListener(e -> sendBarcodeScanStatus(scanPurpose, "error", "No se pudo abrir el escáner. Verificá Google Play Services e intentá nuevamente."));
        } catch (Exception e) {
            sendBarcodeScanStatus(scanPurpose, "error", "No se pudo iniciar el escáner de código de barras.");
        }
    }

    private static class BarcodeLookupResult {
        String name = "";
        String source = "barcode";
    }

    private void lookupBarcodeAndReturn(String code, String purpose) {
        BarcodeLookupResult result = lookupBarcodeDomain("https://world.openfoodfacts.org", code, "Open Food Facts");
        if (result.name.isEmpty()) {
            BarcodeLookupResult fallback = lookupBarcodeDomain("https://world.openproductsfacts.org", code, "Open Products Facts");
            if (!fallback.name.isEmpty()) result = fallback;
        }
        sendBarcodeLookupResult(purpose, code, result.name, result.source);
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
            c.setRequestProperty("User-Agent", "AlDia/2.21 Android barcode lookup");
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

    private void launchTextPhoto(String purpose) {
        String safePurpose = purpose == null ? "" : purpose.trim();
        if ("expiry_ticket".equals(safePurpose)) {
            launchTicketDocumentScanner(safePurpose);
            return;
        }
        launchBasicTextPhoto(safePurpose);
    }

    private void launchTicketDocumentScanner(String purpose) {
        pendingPhotoPurpose = purpose == null ? "expiry_ticket" : purpose;
        pendingPhotoFile = null;
        try {
            GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build();
            GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
            sendPhotoScanStatus(pendingPhotoPurpose, "opening", "Abriendo escáner optimizado de ticket…");
            scanner.getStartScanIntent(this)
                    .addOnSuccessListener(intentSender -> {
                        try {
                            startIntentSenderForResult(intentSender, DOCUMENT_SCAN_REQUEST, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            sendPhotoScanStatus(pendingPhotoPurpose, "fallback", "No se pudo abrir el escáner optimizado. Usando cámara normal…");
                            launchBasicTextPhoto(pendingPhotoPurpose);
                        }
                    })
                    .addOnFailureListener(e -> {
                        String fallbackPurpose = pendingPhotoPurpose;
                        sendPhotoScanStatus(fallbackPurpose, "fallback", "Escáner optimizado no disponible. Usando cámara normal…");
                        launchBasicTextPhoto(fallbackPurpose);
                    });
        } catch (Exception e) {
            String fallbackPurpose = pendingPhotoPurpose;
            sendPhotoScanStatus(fallbackPurpose, "fallback", "Escáner optimizado no disponible. Usando cámara normal…");
            launchBasicTextPhoto(fallbackPurpose);
        }
    }

    private void launchBasicTextPhoto(String purpose) {
        try {
            File dir = new File(getCacheDir(), "capture");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("No se pudo preparar la cámara");
            pendingPhotoFile = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            pendingPhotoPurpose = purpose == null ? "" : purpose;
            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority(getPackageName() + ".files")
                    .appendPath("capture")
                    .appendPath(pendingPhotoFile.getName())
                    .build();
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            camera.setClipData(ClipData.newRawUri("Al Día", uri));
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            sendPhotoScanStatus(pendingPhotoPurpose, "opening", "Abriendo cámara…");
            startActivityForResult(camera, PHOTO_CAPTURE_REQUEST);
        } catch (Exception e) {
            pendingPhotoFile = null;
            sendPhotoScanStatus(purpose, "error", "No se pudo abrir la cámara.");
        }
    }

    private void processCapturedPhoto() {
        final File file = pendingPhotoFile;
        final String purpose = pendingPhotoPurpose;
        pendingPhotoFile = null;
        pendingPhotoPurpose = "";
        if (file == null || !file.exists()) {
            sendPhotoScanStatus(purpose, "error", "No se encontró la foto capturada.");
            return;
        }
        try {
            Uri captureUri = new Uri.Builder()
                    .scheme("content")
                    .authority(getPackageName() + ".files")
                    .appendPath("capture")
                    .appendPath(file.getName())
                    .build();
            processPhotoUri(captureUri, purpose, file, false);
        } catch (Exception e) {
            sendPhotoScanStatus(purpose, "error", "No se pudo procesar la foto.");
            try { file.delete(); } catch (Exception ignored) { }
        }
    }

    private static class OcrPassData {
        String text = "";
        JSONObject json = new JSONObject();
        int dateLikeCount = 0;
        boolean hasPlu = false;
        float averageConfidence = 0f;
    }

    private void processPhotoUri(Uri imageUri, String purpose, File cleanupFile, boolean optimizedDocument) {
        if (imageUri == null) {
            sendPhotoScanStatus(purpose, "error", "No se encontró la imagen del ticket.");
            cleanupPhotoFile(cleanupFile);
            return;
        }
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            sendPhotoScanStatus(purpose, "reading", optimizedDocument ? "Leyendo ticket corregido y mejorado…" : "Leyendo texto de la foto…");
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        OcrPassData first = buildOcrPass(text, "principal");
                        recognizer.close();
                        if ("expiry_ticket".equals(purpose)) {
                            // En tickets hacemos siempre dos lecturas y luego fusionamos resultados.
                            // La segunda pasada en gris/alto contraste ayuda cuando la primera lectura
                            // parece válida pero confunde L/Etq, Vence o algún dígito del PLU.
                            sendPhotoScanStatus(purpose, "enhancing", needsEnhancedTicketPass(first)
                                    ? "Reforzando lectura de PLU y fechas con alto contraste…"
                                    : "Verificando PLU y vencimiento con una segunda lectura…");
                            runEnhancedTicketPass(imageUri, purpose, first, cleanupFile, optimizedDocument);
                        } else {
                            sendPhotoOcrResult(purpose, first, null, optimizedDocument);
                            sendPhotoScanStatus(purpose, "done", optimizedDocument ? "Ticket procesado." : "Foto procesada.");
                            cleanupPhotoFile(cleanupFile);
                        }
                    })
                    .addOnFailureListener(e -> {
                        recognizer.close();
                        sendPhotoScanStatus(purpose, "error", "No se pudo leer el texto. Podés completar los datos manualmente.");
                        cleanupPhotoFile(cleanupFile);
                    });
        } catch (Exception e) {
            sendPhotoScanStatus(purpose, "error", "No se pudo procesar la imagen.");
            cleanupPhotoFile(cleanupFile);
        }
    }

    private void runEnhancedTicketPass(Uri imageUri, String purpose, OcrPassData first, File cleanupFile, boolean optimizedDocument) {
        Bitmap source = null;
        Bitmap enhanced = null;
        TextRecognizer recognizer = null;
        try {
            source = decodeScaledBitmap(imageUri, 2200);
            if (source == null) throw new Exception("No se pudo preparar la imagen");
            enhanced = enhanceTicketBitmap(source);
            final Bitmap sourceFinal = source;
            final Bitmap enhancedFinal = enhanced;
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            final TextRecognizer recognizerFinal = recognizer;
            recognizer.process(InputImage.fromBitmap(enhanced, 0))
                    .addOnSuccessListener(text -> {
                        OcrPassData second = buildOcrPass(text, "alto_contraste");
                        sendPhotoOcrResult(purpose, first, second, optimizedDocument);
                        sendPhotoScanStatus(purpose, "done", "Ticket procesado con doble lectura.");
                        recognizerFinal.close();
                        recycleBitmap(enhancedFinal);
                        recycleBitmap(sourceFinal);
                        cleanupPhotoFile(cleanupFile);
                    })
                    .addOnFailureListener(e -> {
                        sendPhotoOcrResult(purpose, first, null, optimizedDocument);
                        sendPhotoScanStatus(purpose, "done", "Ticket procesado. La segunda lectura no fue necesaria para continuar.");
                        recognizerFinal.close();
                        recycleBitmap(enhancedFinal);
                        recycleBitmap(sourceFinal);
                        cleanupPhotoFile(cleanupFile);
                    });
        } catch (Exception e) {
            if (recognizer != null) try { recognizer.close(); } catch (Exception ignored) { }
            recycleBitmap(enhanced);
            recycleBitmap(source);
            sendPhotoOcrResult(purpose, first, null, optimizedDocument);
            sendPhotoScanStatus(purpose, "done", "Ticket procesado.");
            cleanupPhotoFile(cleanupFile);
        }
    }

    private Bitmap decodeScaledBitmap(Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > maxSide * 2) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                Bitmap decoded = BitmapFactory.decodeStream(input, null, opts);
                if (decoded == null) return null;
                int currentMax = Math.max(decoded.getWidth(), decoded.getHeight());
                if (currentMax <= maxSide) return decoded;
                float scale = maxSide / (float) currentMax;
                Bitmap scaled = Bitmap.createScaledBitmap(decoded,
                        Math.max(1, Math.round(decoded.getWidth() * scale)),
                        Math.max(1, Math.round(decoded.getHeight() * scale)), true);
                if (scaled != decoded) decoded.recycle();
                return scaled;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap enhanceTicketBitmap(Bitmap source) {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        float contrast = 1.45f;
        float translate = (-0.5f * contrast + 0.5f) * 255f + 8f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });
        matrix.postConcat(contrastMatrix);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return out;
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try { bitmap.recycle(); } catch (Exception ignored) { }
        }
    }

    private void cleanupPhotoFile(File file) {
        if (file != null) try { file.delete(); } catch (Exception ignored) { }
    }

    private boolean needsEnhancedTicketPass(OcrPassData pass) {
        if (pass == null) return true;
        if (!pass.hasPlu || pass.dateLikeCount < 2) return true;
        return pass.averageConfidence > 0f && pass.averageConfidence < 0.66f;
    }

    private OcrPassData buildOcrPass(Text text, String label) {
        OcrPassData out = new OcrPassData();
        try {
            List<Text.Line> lines = new ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) {
                if (block != null) lines.addAll(block.getLines());
            }
            lines.sort(new Comparator<Text.Line>() {
                @Override
                public int compare(Text.Line a, Text.Line b) {
                    Rect ra = a == null ? null : a.getBoundingBox();
                    Rect rb = b == null ? null : b.getBoundingBox();
                    if (ra == null && rb == null) return 0;
                    if (ra == null) return 1;
                    if (rb == null) return -1;
                    int centerAy = ra.centerY(), centerBy = rb.centerY();
                    int tolerance = Math.max(6, Math.min(ra.height(), rb.height()) / 2);
                    if (Math.abs(centerAy - centerBy) > tolerance) return Integer.compare(centerAy, centerBy);
                    return Integer.compare(ra.left, rb.left);
                }
            });

            int minLeft = Integer.MAX_VALUE, minTop = Integer.MAX_VALUE, maxRight = 0, maxBottom = 0;
            for (Text.Line line : lines) {
                Rect r = line == null ? null : line.getBoundingBox();
                if (r == null) continue;
                minLeft = Math.min(minLeft, r.left);
                minTop = Math.min(minTop, r.top);
                maxRight = Math.max(maxRight, r.right);
                maxBottom = Math.max(maxBottom, r.bottom);
            }
            if (minLeft == Integer.MAX_VALUE) minLeft = 0;
            if (minTop == Integer.MAX_VALUE) minTop = 0;
            float spanX = Math.max(1f, maxRight - minLeft);
            float spanY = Math.max(1f, maxBottom - minTop);

            JSONArray jsonLines = new JSONArray();
            StringBuilder ordered = new StringBuilder();
            float confidenceSum = 0f;
            int confidenceCount = 0;
            for (Text.Line line : lines) {
                if (line == null) continue;
                String value = line.getText() == null ? "" : line.getText().trim();
                if (value.isEmpty()) continue;
                if (ordered.length() > 0) ordered.append("\n");
                ordered.append(value);
                Rect r = line.getBoundingBox();
                float confidence = 0f;
                try { confidence = line.getConfidence(); } catch (Exception ignored) { }
                if (confidence > 0f) { confidenceSum += confidence; confidenceCount++; }
                JSONObject item = new JSONObject();
                item.put("text", value);
                item.put("confidence", confidence);
                item.put("angle", line.getAngle());
                if (r != null) {
                    item.put("x", (r.left - minLeft) / spanX);
                    item.put("y", (r.top - minTop) / spanY);
                    item.put("w", r.width() / spanX);
                    item.put("h", r.height() / spanY);
                    item.put("cx", (r.centerX() - minLeft) / spanX);
                    item.put("cy", (r.centerY() - minTop) / spanY);
                }
                jsonLines.put(item);
            }
            out.text = ordered.length() > 0 ? ordered.toString() : (text == null || text.getText() == null ? "" : text.getText());
            out.dateLikeCount = countDateLike(out.text);
            out.hasPlu = hasPluLike(out.text);
            out.averageConfidence = confidenceCount > 0 ? confidenceSum / confidenceCount : 0f;
            JSONObject json = new JSONObject();
            json.put("label", label == null ? "" : label);
            json.put("text", out.text);
            json.put("lineCount", jsonLines.length());
            json.put("dateLikeCount", out.dateLikeCount);
            json.put("hasPlu", out.hasPlu);
            json.put("averageConfidence", out.averageConfidence);
            json.put("lines", jsonLines);
            out.json = json;
        } catch (Exception e) {
            out.text = orderedOcrText(text);
        }
        return out;
    }

    private int countDateLike(String text) {
        if (text == null || text.isEmpty()) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?<![0-9A-Z])[0-9OQIL\\|]{1,2}[\\/\\.\\-][0-9OQIL\\|]{1,2}[\\/\\.\\-][0-9OQIL\\|]{2,4}(?![0-9A-Z])")
                .matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private boolean hasPluLike(String text) {
        if (text == null) return false;
        return java.util.regex.Pattern
                .compile("(?i)P\\s*[L1I\\|]?\\s*U\\s*[:#\\-]?\\s*[0-9OQIL\\|]{3,8}")
                .matcher(text)
                .find();
    }

    private int criticalOcrScore(OcrPassData pass) {
        if (pass == null) return -1;
        int score = pass.dateLikeCount * 20 + (pass.hasPlu ? 35 : 0) + Math.min(20, pass.text.length() / 30);
        if (pass.averageConfidence > 0f) score += Math.round(pass.averageConfidence * 10f);
        return score;
    }

    private void sendPhotoOcrResult(String purpose, OcrPassData first, OcrPassData second, boolean optimizedDocument) {
        if (webView == null) return;
        OcrPassData primary = second != null && criticalOcrScore(second) > criticalOcrScore(first) ? second : first;
        JSONObject meta = new JSONObject();
        try {
            JSONArray passes = new JSONArray();
            if (first != null) passes.put(first.json);
            if (second != null) passes.put(second.json);
            meta.put("optimizedDocument", optimizedDocument);
            meta.put("passes", passes);
        } catch (Exception ignored) { }
        String js = "window.onPhotoTextResult && window.onPhotoTextResult(" + JSONObject.quote(purpose == null ? "" : purpose) + "," + JSONObject.quote(primary == null ? "" : primary.text) + "," + meta.toString() + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private String orderedOcrText(Text text) {
        if (text == null) return "";
        try {
            List<Text.Line> lines = new ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) {
                if (block != null) lines.addAll(block.getLines());
            }
            lines.sort(new Comparator<Text.Line>() {
                @Override
                public int compare(Text.Line a, Text.Line b) {
                    Rect ra = a == null ? null : a.getBoundingBox();
                    Rect rb = b == null ? null : b.getBoundingBox();
                    if (ra == null && rb == null) return 0;
                    if (ra == null) return 1;
                    if (rb == null) return -1;
                    if (ra.top != rb.top) return Integer.compare(ra.top, rb.top);
                    return Integer.compare(ra.left, rb.left);
                }
            });
            StringBuilder out = new StringBuilder();
            for (Text.Line line : lines) {
                if (line == null) continue;
                String value = line.getText() == null ? "" : line.getText().trim();
                if (value.isEmpty()) continue;
                if (out.length() > 0) out.append("\n");
                out.append(value);
            }
            String result = out.toString().trim();
            return result.isEmpty() ? text.getText() : result;
        } catch (Exception ignored) {
            return text.getText() == null ? "" : text.getText();
        }
    }

    private void sendBarcodeScanStatus(String purpose, String status, String message) {
        if (webView == null) return;
        String js = "window.onBarcodeScanStatusFor && window.onBarcodeScanStatusFor(" + JSONObject.quote(purpose) + "," + JSONObject.quote(status) + "," + JSONObject.quote(message) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendBarcodeScanResult(String purpose, String code, String name, String source) {
        if (webView == null) return;
        String js = "window.onBarcodeScanResultFor && window.onBarcodeScanResultFor(" + JSONObject.quote(purpose) + "," + JSONObject.quote(code) + "," + JSONObject.quote(name == null ? "" : name) + "," + JSONObject.quote(source == null ? "barcode" : source) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendBarcodeLookupResult(String purpose, String code, String name, String source) {
        if (webView == null) return;
        String js = "window.onBarcodeLookupResultFor && window.onBarcodeLookupResultFor(" + JSONObject.quote(purpose) + "," + JSONObject.quote(code) + "," + JSONObject.quote(name == null ? "" : name) + "," + JSONObject.quote(source == null ? "barcode" : source) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendPhotoScanStatus(String purpose, String status, String message) {
        if (webView == null) return;
        String js = "window.onPhotoScanStatus && window.onPhotoScanStatus(" + JSONObject.quote(purpose == null ? "" : purpose) + "," + JSONObject.quote(status) + "," + JSONObject.quote(message) + ");";
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
        if (requestCode == DOCUMENT_SCAN_REQUEST) {
            String purpose = pendingPhotoPurpose;
            pendingPhotoPurpose = "";
            if (resultCode == RESULT_OK) {
                try {
                    GmsDocumentScanningResult scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data);
                    List<GmsDocumentScanningResult.Page> pages = scanResult == null ? null : scanResult.getPages();
                    if (pages == null || pages.isEmpty() || pages.get(0).getImageUri() == null) {
                        sendPhotoScanStatus(purpose, "error", "El escáner no devolvió una imagen. Intentá nuevamente.");
                    } else {
                        processPhotoUri(pages.get(0).getImageUri(), purpose, null, true);
                    }
                } catch (Exception e) {
                    sendPhotoScanStatus(purpose, "error", "No se pudo procesar el ticket escaneado.");
                }
            } else {
                sendPhotoScanStatus(purpose, "canceled", "Escaneo cancelado.");
            }
            return;
        }
        if (requestCode == PHOTO_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK) processCapturedPhoto();
            else {
                String purpose = pendingPhotoPurpose;
                if (pendingPhotoFile != null) try { pendingPhotoFile.delete(); } catch (Exception ignored) { }
                pendingPhotoFile = null; pendingPhotoPurpose = "";
                sendPhotoScanStatus(purpose, "canceled", "Foto cancelada.");
            }
            return;
        }
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
