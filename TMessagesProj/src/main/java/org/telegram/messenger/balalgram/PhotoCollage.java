package org.telegram.messenger.balalgram;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayoutPreview;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Stitches 2-10 selected photos into a single collage JPEG PhotoEntry using the album layout calculator.
 */
public final class PhotoCollage {

    private static final int MAX_PHOTOS = 10;
    private static final float MAX_SIZE_HEIGHT = 814.0f;
    private static final int QUALITY = 90;

    private PhotoCollage() {
    }

    public static ArrayList<MediaController.PhotoEntry> collect(
            HashMap<Object, Object> selectedPhotos,
            ArrayList<Object> selectedPhotosOrder
    ) {
        if (selectedPhotos == null || selectedPhotosOrder == null) {
            return null;
        }
        if (selectedPhotosOrder.size() < 2 || selectedPhotosOrder.size() > MAX_PHOTOS) {
            return null;
        }
        ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>(selectedPhotosOrder.size());
        for (Object key : selectedPhotosOrder) {
            Object raw = selectedPhotos.get(key);
            if (!(raw instanceof MediaController.PhotoEntry)) {
                return null;
            }
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) raw;
            if (photoEntry.isVideo || photoEntry.ttl != 0 || TextUtils.isEmpty(sourcePath(photoEntry))) {
                return null;
            }
            photos.add(photoEntry);
        }
        return photos;
    }

    public static MediaController.PhotoEntry create(ArrayList<MediaController.PhotoEntry> photos) {
        if (photos == null || photos.size() < 2) {
            return null;
        }
        ChatAttachAlertPhotoLayoutPreview.GroupCalculator group =
                new ChatAttachAlertPhotoLayoutPreview.GroupCalculator(photos, 0);
        if (group.width <= 0 || group.height <= 0f) {
            return null;
        }
        boolean highQuality = false;
        for (MediaController.PhotoEntry entry : photos) {
            if (entry.isHighQuality()) {
                highQuality = true;
                break;
            }
        }
        float sizeWidth = (float) group.width;
        float sizeHeight = group.height * MAX_SIZE_HEIGHT;
        float scale = AndroidUtilities.getPhotoSize(highQuality) / Math.max(sizeWidth, sizeHeight);
        int resultWidth = Math.max(1, Math.round(sizeWidth * scale));
        int resultHeight = Math.max(1, Math.round(sizeHeight * scale));

        Bitmap result = null;
        try {
            Bitmap collage = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
            result = collage;
            Canvas canvas = new Canvas(collage);
            canvas.drawColor(Color.BLACK);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            Rect src = new Rect();
            Rect dst = new Rect();
            for (MediaController.PhotoEntry photoEntry : photos) {
                MessageObject.GroupedMessagePosition position = group.positions.get(photoEntry);
                if (position == null) {
                    continue;
                }
                dst.set(
                        Math.round(position.left / group.width * resultWidth),
                        Math.round(position.top / group.height * resultHeight),
                        Math.round((position.left + position.pw) / group.width * resultWidth),
                        Math.round((position.top + position.ph) / group.height * resultHeight)
                );
                if (dst.isEmpty()) {
                    continue;
                }
                Bitmap bitmap = ImageLoader.loadBitmap(
                        sourcePath(photoEntry), null, (float) dst.width(), (float) dst.height(), true
                );
                if (bitmap == null) {
                    continue;
                }
                float cropScale = Math.max(
                        dst.width() / (float) bitmap.getWidth(),
                        dst.height() / (float) bitmap.getHeight()
                );
                int srcWidth = Math.min(bitmap.getWidth(), Math.max(1, Math.round(dst.width() / cropScale)));
                int srcHeight = Math.min(bitmap.getHeight(), Math.max(1, Math.round(dst.height() / cropScale)));
                int srcLeft = (bitmap.getWidth() - srcWidth) / 2;
                int srcTop = (bitmap.getHeight() - srcHeight) / 2;
                src.set(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight);
                canvas.drawBitmap(bitmap, src, dst, paint);
                bitmap.recycle();
            }
            File file = new File(
                    FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                    SharedConfig.getLastLocalId() + "_collage.jpg"
            );
            try (FileOutputStream stream = new FileOutputStream(file)) {
                collage.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream);
            }
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(
                    0, SharedConfig.getLastLocalId(), 0L, file.getAbsolutePath(), 0, false,
                    resultWidth, resultHeight, file.length()
            );
            photoEntry.canDeleteAfter = true;
            photoEntry.highQuality = highQuality;
            return photoEntry;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (result != null) {
                result.recycle();
            }
        }
    }

    private static String sourcePath(MediaController.PhotoEntry photoEntry) {
        return photoEntry.imagePath != null ? photoEntry.imagePath : photoEntry.path;
    }
}
