package org.telegram.messenger.balalgram;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.Bulletin;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks queued local-file deletions tied to outgoing media send tokens.
 */
public final class DeleteAfterSend {

    private static final String PARAM = "balalDeleteAfterSend";
    private static final long EXPIRE_MS = 24L * 60L * 60L * 1000L;

    private static final class Source {
        final String path;
        final long device;
        final long inode;
        final long length;
        final long modified;

        Source(String path, long device, long inode, long length, long modified) {
            this.path = path;
            this.device = device;
            this.inode = inode;
            this.length = length;
            this.modified = modified;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Source)) return false;
            Source source = (Source) o;
            return device == source.device
                    && inode == source.inode
                    && length == source.length
                    && modified == source.modified
                    && Objects.equals(path, source.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, device, inode, length, modified);
        }
    }

    private static final class Pending {
        final Source source;
        final int imageId;
        final long expires;

        Pending(Source source, int imageId, long expires) {
            this.source = source;
            this.imageId = imageId;
            this.expires = expires;
        }
    }

    private static final HashMap<String, Pending> pending = new HashMap<>();

    private DeleteAfterSend() {
    }

    public static void arm(HashMap<String, String> params, String deletePath, int imageId) {
        if (params == null || TextUtils.isEmpty(deletePath)) {
            return;
        }
        Source source = snapshot(deletePath);
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        synchronized (pending) {
            Iterator<Pending> iterator = pending.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().expires < now) {
                    iterator.remove();
                }
            }
            pending.put(token, new Pending(source, imageId, now + EXPIRE_MS));
        }
        params.put(PARAM, token);
    }

    public static void onMessageSent(HashMap<String, String> params, boolean scheduled) {
        final Pending entry = take(params);
        if (entry == null || scheduled || entry.expires < System.currentTimeMillis()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            Source source = entry.source;
            boolean deleted;
            if (source == null || !source.equals(snapshot(source.path))) {
                deleted = false;
            } else if (entry.imageId > 0) {
                deleted = deleteMedia(source, entry.imageId);
            } else {
                deleted = deleteFile(source);
            }
            AndroidUtilities.runOnUIThread(() -> showResult(deleted));
        });
    }

    public static void disarm(HashMap<String, String> params) {
        take(params);
    }

    private static Pending take(HashMap<String, String> params) {
        if (params == null) {
            return null;
        }
        String token = params.get(PARAM);
        if (token == null) {
            return null;
        }
        synchronized (pending) {
            return pending.remove(token);
        }
    }

    private static Source snapshot(String path) {
        try {
            File file = new File(path);
            StructStat state = Os.lstat(file.getAbsolutePath());
            if (!OsConstants.S_ISREG(state.st_mode)) {
                return null;
            }
            return new Source(file.getCanonicalPath(), state.st_dev, state.st_ino, state.st_size, file.lastModified());
        } catch (Exception error) {
            FileLog.e(error);
            return null;
        }
    }

    private static boolean deleteMedia(Source source, int imageId) {
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
        try {
            String path = null;
            try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    path = cursor.getString(0);
                }
            }
            if (path == null) {
                return false;
            }
            Source mediaSource = snapshot(path);
            return source.equals(mediaSource) && resolver.delete(uri, null, null) > 0;
        } catch (Exception error) {
            FileLog.e(error);
            return false;
        }
    }

    private static boolean deleteFile(Source source) {
        try {
            return new File(source.path).delete();
        } catch (Exception error) {
            FileLog.e(error);
            return false;
        }
    }

    private static void showResult(boolean deleted) {
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.showBulletin,
                deleted ? Bulletin.TYPE_SUCCESS : Bulletin.TYPE_ERROR,
                LocaleController.getString(deleted ? R.string.DeleteFromDeviceDone : R.string.DeleteFromDeviceFailed)
        );
    }
}
