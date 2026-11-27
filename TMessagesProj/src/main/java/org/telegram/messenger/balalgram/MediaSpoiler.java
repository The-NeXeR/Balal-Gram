package org.telegram.messenger.balalgram;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * Helpers for checking and toggling Telegram media spoilers on existing messages.
 */
public final class MediaSpoiler {

    private MediaSpoiler() {
    }

    public static boolean canToggle(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.getId() <= 0 || messageObject.isSecretMedia() || messageObject.needDrawBluredPreview() || messageObject.isSensitive()) {
            return false;
        }
        if (messageObject.type != MessageObject.TYPE_PHOTO
                && messageObject.type != MessageObject.TYPE_VIDEO
                && messageObject.type != MessageObject.TYPE_GIF) {
            return false;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return media.photo != null && media.photo.access_hash != 0L;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            return media.document != null && media.document.access_hash != 0L;
        }
        return false;
    }

    public static boolean isSpoilered(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        return media != null && media.spoiler;
    }

    public static TLRPC.InputMedia inputMedia(MessageObject messageObject, boolean spoiler) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        if (media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null) {
            TLRPC.TL_inputMediaPhoto inputMedia = new TLRPC.TL_inputMediaPhoto();
            inputMedia.id = inputPhoto(media.photo);
            inputMedia.spoiler = spoiler;
            if (media.ttl_seconds != 0) {
                inputMedia.ttl_seconds = media.ttl_seconds;
                inputMedia.flags |= TLObject.FLAG_0;
            }
            if (media.live_photo && media.document != null) {
                inputMedia.video = inputDocument(media.document);
            }
            return inputMedia;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            TLRPC.TL_inputMediaDocument inputMedia = new TLRPC.TL_inputMediaDocument();
            inputMedia.id = inputDocument(media.document);
            inputMedia.spoiler = spoiler;
            if (media.ttl_seconds != 0) {
                inputMedia.ttl_seconds = media.ttl_seconds;
                inputMedia.flags |= TLObject.FLAG_0;
            }
            if (media.video_cover != null) {
                inputMedia.video_cover = inputPhoto(media.video_cover);
                inputMedia.flags |= TLObject.FLAG_3;
            }
            if (media.video_timestamp != 0) {
                inputMedia.video_timestamp = media.video_timestamp;
                inputMedia.flags |= TLObject.FLAG_4;
            }
            return inputMedia;
        }
        return null;
    }

    private static TLRPC.TL_inputPhoto inputPhoto(TLRPC.Photo photo) {
        TLRPC.TL_inputPhoto input = new TLRPC.TL_inputPhoto();
        input.id = photo.id;
        input.access_hash = photo.access_hash;
        input.file_reference = photo.file_reference != null ? photo.file_reference : new byte[0];
        return input;
    }

    private static TLRPC.TL_inputDocument inputDocument(TLRPC.Document document) {
        TLRPC.TL_inputDocument input = new TLRPC.TL_inputDocument();
        input.id = document.id;
        input.access_hash = document.access_hash;
        input.file_reference = document.file_reference != null ? document.file_reference : new byte[0];
        return input;
    }
}
