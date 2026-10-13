package kr.hyun.fishing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    static final String APP_URL = "https://hyun-fishing.netlify.app/";
    static final int REQ_PICK = 11, REQ_FILE = 12, REQ_PERM_MEDIA = 21, REQ_PERM_LOC = 22, REQ_PERM_CAM = 23;

    WebView web;
    String pendingKind = "trip";
    ValueCallback<Uri[]> fileCb;
    Uri camUri;
    GeolocationPermissions.Callback geoCb; String geoOrigin;
    Runnable afterCamPerm;
    final Handler ui = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true); s.setAllowFileAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " FishingLogApp/1.0");
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u.getHost() != null && u.getHost().endsWith("hyun-fishing.netlify.app")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (has(Manifest.permission.ACCESS_FINE_LOCATION)) cb.invoke(origin, true, true);
                else { geoCb = cb; geoOrigin = origin; ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERM_LOC); }
            }
            @Override public boolean onCreateWindow(WebView v, boolean d, boolean u, android.os.Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) {}
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp); msg.sendToTarget(); return true;
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Runnable go = () -> {
                    try {
                        if (p.isCaptureEnabled()) {
                            File dir = new File(getCacheDir(), "cam"); dir.mkdirs();
                            File f = new File(dir, "cam_" + System.currentTimeMillis() + ".jpg");
                            camUri = FileProvider.getUriForFile(MainActivity.this, "kr.hyun.fishing.fileprovider", f);
                            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, camUri)
                                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivityForResult(i, REQ_FILE);
                        } else {
                            Intent i = p.createIntent(); startActivityForResult(i, REQ_FILE);
                        }
                    } catch (Exception e) { fileCb.onReceiveValue(null); fileCb = null; }
                };
                if (p.isCaptureEnabled() && !has(Manifest.permission.CAMERA)) {
                    afterCamPerm = go; ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQ_PERM_CAM);
                } else go.run();
                return true;
            }
        });
        web.loadUrl(APP_URL);
    }

    boolean has(String perm) { return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED; }

    class Bridge {
        @JavascriptInterface public boolean isNative() { return true; }
        @JavascriptInterface public void pickPhotos(String kind) {
            pendingKind = kind == null ? "trip" : kind;
            ui.post(() -> {
                List<String> need = new ArrayList<>();
                if (Build.VERSION.SDK_INT >= 33) { if (!has(Manifest.permission.READ_MEDIA_IMAGES)) need.add(Manifest.permission.READ_MEDIA_IMAGES); }
                else if (!has(Manifest.permission.READ_EXTERNAL_STORAGE)) need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                if (!has(Manifest.permission.ACCESS_MEDIA_LOCATION)) need.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
                if (need.isEmpty()) openPicker();
                else ActivityCompat.requestPermissions(MainActivity.this, need.toArray(new String[0]), REQ_PERM_MEDIA);
            });
        }
    }

    void openPicker() {
        Intent i = new Intent(this, PickerActivity.class).putExtra("max", "boat".equals(pendingKind) ? 1 : 30);
        startActivityForResult(i, REQ_PICK);
    }

    @Override public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        boolean ok = g.length > 0; for (int r : g) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
        if (req == REQ_PERM_MEDIA) {
            if (ok) openPicker();
            else Toast.makeText(this, "사진·위치 권한을 허용해야 사진 위치를 읽을 수 있어요", Toast.LENGTH_LONG).show();
        } else if (req == REQ_PERM_LOC && geoCb != null) { geoCb.invoke(geoOrigin, ok, true); geoCb = null; }
        else if (req == REQ_PERM_CAM) { if (ok && afterCamPerm != null) afterCamPerm.run(); else if (fileCb != null) { fileCb.onReceiveValue(null); fileCb = null; } afterCamPerm = null; }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            if (fileCb == null) return;
            Uri[] out = null;
            if (res == Activity.RESULT_OK) {
                if (data != null && data.getClipData() != null) { ClipData c = data.getClipData(); out = new Uri[c.getItemCount()]; for (int i = 0; i < c.getItemCount(); i++) out[i] = c.getItemAt(i).getUri(); }
                else if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
                else if (camUri != null) out = new Uri[]{camUri};
            }
            fileCb.onReceiveValue(out); fileCb = null; camUri = null;
        } else if (req == REQ_PICK && res == Activity.RESULT_OK && data != null) {
            ArrayList<Uri> uris = data.getParcelableArrayListExtra("uris");
            if (uris == null || uris.isEmpty()) return;
            final String kind = pendingKind;
            Toast.makeText(this, "사진 " + uris.size() + "장 읽는 중…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                JSONArray arr = new JSONArray();
                for (Uri u : uris) { try { arr.put(readPhoto(u)); } catch (Exception e) {} }
                final String js = "window.onNativePhotos&&window.onNativePhotos(" + JSONObject.quote(kind) + "," + arr.toString() + ")";
                ui.post(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }

    JSONObject readPhoto(Uri uri) throws Exception {
        JSONObject o = new JSONObject();
        Uri orig = uri;
        try { orig = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) {}
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(orig)) {
            ExifInterface ex = new ExifInterface(in);
            double[] ll = ex.getLatLong();
            if (ll != null && (ll[0] != 0 || ll[1] != 0)) { o.put("lat", ll[0]); o.put("lng", ll[1]); }
            String dt = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dt == null) dt = ex.getAttribute(ExifInterface.TAG_DATETIME);
            if (dt != null && dt.length() >= 16) o.put("date", dt.substring(0, 4) + "-" + dt.substring(5, 7) + "-" + dt.substring(8, 10) + "T" + dt.substring(11, 16));
            int or = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
        }
        if (!o.has("date")) {
            try (android.database.Cursor c = getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATE_TAKEN}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) { long t = c.getLong(0); java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US); o.put("date", f.format(new java.util.Date(t))); }
            } catch (Exception ignored) {}
        }
        // 축소 이미지
        BitmapFactory.Options op = new BitmapFactory.Options(); op.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, op); }
        int max = Math.max(op.outWidth, op.outHeight), sample = 1;
        while (max / sample > 1600) sample *= 2;
        op = new BitmapFactory.Options(); op.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(uri)) { bm = BitmapFactory.decodeStream(in, null, op); }
        if (bm == null) throw new Exception("decode fail");
        float sc = Math.min(1f, 1200f / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix(); m.postScale(sc, sc); if (rot != 0) m.postRotate(rot);
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 80, bo);
        o.put("data", "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
        return o;
    }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
