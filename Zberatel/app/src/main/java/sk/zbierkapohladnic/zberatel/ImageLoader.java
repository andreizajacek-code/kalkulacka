package sk.zbierkapohladnic.zberatel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {}

    public static void load(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        view.setTag(url);

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        POOL.execute(() -> {
            Bitmap bmp = download(url);
            if (bmp == null) return;
            CACHE.put(url, bmp);
            view.post(() -> {
                Object tag = view.getTag();
                if (tag != null && url.equals(tag.toString())) view.setImageBitmap(bmp);
            });
        });
    }

    private static Bitmap download(String address) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection)new URL(address).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            int code = con.getResponseCode();
            if (code < 200 || code >= 400) return null;

            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 2_500_000) {
                    in.close();
                    return null;
                }
                out.write(buf,0,n);
            }
            in.close();
            byte[] data = out.toByteArray();

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);

            int sample = 1;
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 900) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1,sample);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data,0,data.length,opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
