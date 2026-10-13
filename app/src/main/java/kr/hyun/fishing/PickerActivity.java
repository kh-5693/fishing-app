package kr.hyun.fishing;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PickerActivity extends AppCompatActivity {
    final List<Long> ids = new ArrayList<>();
    final List<Long> dates = new ArrayList<>();
    final LinkedHashSet<Long> sel = new LinkedHashSet<>();
    final LruCache<Long, Bitmap> cache = new LruCache<>(300);
    final ExecutorService pool = Executors.newFixedThreadPool(3);
    final Handler ui = new Handler(Looper.getMainLooper());
    int max = 30; Button done; TextView title;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_picker);
        max = getIntent().getIntExtra("max", 30);
        done = findViewById(R.id.btnDone); title = findViewById(R.id.title);
        findViewById(R.id.btnCancel).setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        done.setOnClickListener(v -> {
            ArrayList<Uri> out = new ArrayList<>();
            for (Long id : sel) out.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            setResult(RESULT_OK, new Intent().putParcelableArrayListExtra("uris", out)); finish();
        });
        load();
        RecyclerView rv = findViewById(R.id.grid);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setAdapter(new Adapter());
        updateTitle();
    }

    void load() {
        String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN};
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC, " + MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c == null) return;
            int n = 0;
            while (c.moveToNext() && n++ < 3000) { ids.add(c.getLong(0)); dates.add(c.isNull(1) ? 0L : c.getLong(1)); }
        } catch (Exception e) { Toast.makeText(this, "사진을 불러올 수 없어요: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    void updateTitle() { title.setText(sel.isEmpty() ? "사진 선택" : sel.size() + "장 선택"); done.setEnabled(!sel.isEmpty()); done.setAlpha(sel.isEmpty() ? 0.4f : 1f); }

    class VH extends RecyclerView.ViewHolder { ImageView img; TextView chk; long id; VH(FrameLayout f, ImageView i, TextView t) { super(f); img = i; chk = t; } }

    class Adapter extends RecyclerView.Adapter<VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            int w = p.getWidth() > 0 ? p.getWidth() / 3 : (int) (120 * getResources().getDisplayMetrics().density);
            FrameLayout f = new FrameLayout(PickerActivity.this);
            f.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w));
            f.setPadding(2, 2, 2, 2);
            ImageView iv = new ImageView(PickerActivity.this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setBackgroundColor(0xFFE7EEF5);
            TextView tv = new TextView(PickerActivity.this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (26 * getResources().getDisplayMetrics().density), (int) (26 * getResources().getDisplayMetrics().density), Gravity.TOP | Gravity.END);
            lp.setMargins(0, 8, 8, 0); tv.setLayoutParams(lp); tv.setGravity(Gravity.CENTER); tv.setTextColor(Color.WHITE); tv.setTextSize(13);
            f.addView(iv); f.addView(tv);
            return new VH(f, iv, tv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            long id = ids.get(pos); h.id = id;
            boolean on = sel.contains(id);
            h.chk.setText(on ? String.valueOf(new ArrayList<>(sel).indexOf(id) + 1) : "");
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL); d.setColor(on ? 0xFFF5A623 : 0x66000000); d.setStroke(3, Color.WHITE);
            h.chk.setBackground(d);
            h.img.setAlpha(on ? 0.6f : 1f);
            Bitmap bm = cache.get(id);
            if (bm != null) h.img.setImageBitmap(bm);
            else {
                h.img.setImageBitmap(null);
                pool.execute(() -> {
                    try {
                        Bitmap t = getContentResolver().loadThumbnail(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), new Size(320, 320), null);
                        cache.put(id, t);
                        ui.post(() -> { if (h.id == id) h.img.setImageBitmap(t); });
                    } catch (Exception ignored) {}
                });
            }
            h.itemView.setOnClickListener(v -> {
                if (sel.contains(id)) sel.remove(id);
                else if (max == 1) { sel.clear(); sel.add(id); }
                else if (sel.size() >= max) { Toast.makeText(PickerActivity.this, "최대 " + max + "장까지 선택할 수 있어요", Toast.LENGTH_SHORT).show(); return; }
                else sel.add(id);
                notifyDataSetChanged(); updateTitle();
            });
        }
        @Override public int getItemCount() { return ids.size(); }
    }

    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }
}
