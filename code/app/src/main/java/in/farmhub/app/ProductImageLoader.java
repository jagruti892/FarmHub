package in.farmhub.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/** Small HTTPS-only image loader that uses platform TLS and no third-party library. */
public final class ProductImageLoader {
    private static final int MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024;
    private static final int MAX_MEMORY_KILOBYTES = 4 * 1024;

    public interface ImageCallback {
        void onImageLoaded(boolean success);
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> bitmapCache =
            new LruCache<String, Bitmap>(MAX_MEMORY_KILOBYTES) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getByteCount() / 1024);
                }
            };

    /** Selects a local representative photo by product name, not broad category. */
    public static int photoForProduct(String productName) {
        String name = productName == null
                ? "" : productName.trim().toLowerCase(Locale.ROOT);
        // Accept the old sample title until its Firestore document is updated.
        if (name.startsWith("certified ")) {
            name = name.substring("certified ".length()).trim();
        }
        switch (name) {
            case "paddy seeds":
                return R.drawable.paddy_seeds;
            case "wheat seeds":
                return R.drawable.wheat_seeds;
            case "vermicompost":
                return R.drawable.vermicompost;
            case "manual knapsack sprayer":
                return R.drawable.knapsack_sprayer;
            case "drip irrigation lateral pipe":
                return R.drawable.drip_irrigation_tubes;
            default:
                return R.drawable.ic_sprout;
        }
    }

    /** Selects a bundled equipment photo by its stable Firestore document ID. */
    private static String assetForEquipment(String equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        switch (equipmentId.trim()) {
            case "equipment_tractor":
                return "equipment/equipment_tractor.jfif";
            case "equipment_power_tiller":
                return "equipment/equipment_power_tiller.jpg";
            case "equipment_knapsack_sprayer":
                return "equipment/equipment_knapsack_sprayer.jpg";
            case "equipment_brush_cutter":
                return "equipment/equipment_brush_cutter.JPG";
            case "equipment_chaff_cutter":
                return "equipment/equipment_chaff_cutter.jfif";
            default:
                return null;
        }
    }

    /** Uses the bundled photo for known sample products; remote loading stays available for others. */
    public void loadForProduct(String imageUrl, String productName, ImageView imageView,
                               ImageCallback callback) {
        int photoResource = photoForProduct(productName);
        if (photoResource == R.drawable.ic_sprout) {
            load(imageUrl, imageView, photoResource, callback);
            return;
        }

        String imageTag = "local:" + productName;
        imageView.setTag(imageTag);
        try {
            imageView.setImageResource(photoResource);
            if (callback != null) {
                imageView.post(() -> {
                    if (imageTag.equals(imageView.getTag())) {
                        callback.onImageLoaded(true);
                    }
                });
            }
        } catch (RuntimeException imageUnavailable) {
            imageView.setImageResource(R.drawable.ic_sprout);
            if (callback != null) {
                callback.onImageLoaded(false);
            }
        }
    }

    /** Uses a bundled photo for known equipment IDs, then falls back to HTTPS. */
    public void loadForEquipment(String equipmentId, String imageUrl, ImageView imageView,
                                 int placeholderResource, ImageCallback callback) {
        String assetPath = assetForEquipment(equipmentId);
        if (assetPath == null) {
            load(imageUrl, imageView, placeholderResource, callback);
            return;
        }

        loadAsset(assetPath, imageView, placeholderResource, callback);
    }

    private void loadAsset(String assetPath, ImageView imageView, int placeholderResource,
                           ImageCallback callback) {
        String cacheKey = "asset:" + assetPath;
        imageView.setImageResource(placeholderResource);
        imageView.setTag(cacheKey);

        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            notifyResult(imageView, cacheKey, callback, true);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = decodeAsset(imageView, assetPath);
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap);
            }
            notifyResult(imageView, cacheKey, callback, bitmap != null, bitmap);
        });
    }

    private Bitmap decodeAsset(ImageView imageView, String assetPath) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = imageView.getContext().getAssets().open(assetPath)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateSampleSize(bounds, 512, 512);
            try (InputStream input = imageView.getContext().getAssets().open(assetPath)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            // Leave the card placeholder visible if a bundled photo is unavailable.
            return null;
        }
    }

    public void load(String imageUrl, ImageView imageView, int placeholderResource,
                     ImageCallback callback) {
        imageView.setImageResource(placeholderResource);
        imageView.setTag(imageUrl);

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            notifyResult(imageView, imageUrl, callback, false);
            return;
        }

        Bitmap cached = bitmapCache.get(imageUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            notifyResult(imageView, imageUrl, callback, true);
            return;
        }

        Uri uri = Uri.parse(imageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            notifyResult(imageView, imageUrl, callback, false);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = downloadBitmap(imageUrl);
            if (bitmap != null) {
                bitmapCache.put(imageUrl, bitmap);
            }
            notifyResult(imageView, imageUrl, callback, bitmap != null, bitmap);
        });
    }

    private Bitmap downloadBitmap(String imageUrl) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                return null;
            }
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                return null;
            }

            byte[] bytes;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int totalBytes = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    totalBytes += count;
                    if (totalBytes > MAX_DOWNLOAD_BYTES) {
                        return null;
                    }
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateSampleSize(bounds, 512, 512);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception ignored) {
            // Keep the built-in placeholder when a remote image is unavailable.
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int calculateSampleSize(BitmapFactory.Options bounds,
                                           int requestedWidth, int requestedHeight) {
        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > requestedWidth
                || bounds.outHeight / sampleSize > requestedHeight) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static void notifyResult(ImageView imageView, String imageUrl,
                                     ImageCallback callback, boolean success) {
        notifyResult(imageView, imageUrl, callback, success, null);
    }

    private static void notifyResult(ImageView imageView, String imageUrl,
                                     ImageCallback callback, boolean success, Bitmap bitmap) {
        imageView.post(() -> {
            if (imageUrl == null ? imageView.getTag() == null
                    : imageUrl.equals(imageView.getTag())) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
                if (callback != null) {
                    callback.onImageLoaded(success);
                }
            }
        });
    }

    public void close() {
        executor.shutdownNow();
        bitmapCache.evictAll();
    }
}
