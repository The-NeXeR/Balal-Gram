package org.telegram.messenger.balalgram;

import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;

import java.util.ArrayList;

/**
 * Helpers for anonymous/as-copy forwarding and grouping media into albums.
 */
public final class AsCopy {

    private AsCopy() {
    }

    public static boolean hasPhotoOrDocument(MessageObject messageObject) {
        return hasPhoto(messageObject) || hasDocument(messageObject);
    }

    public static boolean hasPhoto(MessageObject messageObject) {
        TLRPC.MessageMedia media = messageObject.messageOwner != null ? messageObject.messageOwner.media : null;
        return media != null && media.photo instanceof TLRPC.TL_photo;
    }

    public static boolean hasDocument(MessageObject messageObject) {
        TLRPC.MessageMedia media = messageObject.messageOwner != null ? messageObject.messageOwner.media : null;
        return media != null && media.document instanceof TLRPC.TL_document;
    }

    public static int takeReplyToDraft(long key, TLRPC.TL_forumTopic keyTopic, int currentAccount, boolean cleanDraft) {
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        TLRPC.DraftMessage draft = accountInstance.getMediaDataController().getDraft(key, 0);
        if (draft != null && draft.reply_to != null) {
            int copyId = draft.reply_to.reply_to_msg_id;
            if (cleanDraft) {
                accountInstance.getMediaDataController().cleanDraft(key, 0, true);
            }
            return copyId;
        }
        return keyTopic != null ? keyTopic.id : 0;
    }

    public static TLRPC.InputReplyTo takeReplyInputToDraft(long key, int currentAccount) {
        TLRPC.DraftMessage draft = AccountInstance.getInstance(currentAccount).getMediaDataController().getDraft(key, 0);
        TLRPC.InputReplyTo replyTo = draft != null ? draft.reply_to : null;
        return (replyTo != null && replyTo.reply_to_msg_id != 0 && !TextUtils.isEmpty(replyTo.quote_text)) ? replyTo : null;
    }

    public static void performForwardFromMyName(
            long key,
            TLRPC.TL_forumTopic keyTopic,
            String text,
            ArrayList<MessageObject> sendingMessageObjects,
            int currentAccount,
            BaseFragment parentFragment,
            boolean notify,
            long monoForumPeerId) {
        final ArrayList<Runnable> queue = new ArrayList<>();
        final boolean saveOriginalCaptions = (text == null);
        final String[] replaceText = new String[]{text};
        final int reply = takeReplyToDraft(key, keyTopic, currentAccount, false);
        final TLRPC.InputReplyTo replyInput = takeReplyInputToDraft(key, currentAccount);
        final int topicId = monoForumPeerId != 0L ? 0 : (keyTopic != null ? keyTopic.id : 0);

        final Runnable[] deque = new Runnable[1];
        deque[0] = () -> {
            if (!queue.isEmpty()) {
                Runnable next = queue.remove(0);
                next.run();
            } else {
                AccountInstance.getInstance(currentAccount).getMediaDataController().cleanDraft(key, 0, true);
            }
        };

        ArrayList<MessageObject> groupedMsgs = new ArrayList<>();
        for (int i = 0; i < sendingMessageObjects.size(); i++) {
            MessageObject msg = sendingMessageObjects.get(i);
            if (msg.getGroupId() != 0L) {
                if (!groupedMsgs.isEmpty() && groupedMsgs.get(0).getGroupId() != msg.getGroupId()) {
                    enqueueAlbum(queue, currentAccount, groupedMsgs, key, reply == 0 ? topicId : reply, parentFragment, consumeReplaceText(saveOriginalCaptions, replaceText), notify, monoForumPeerId, deque, replyInput);
                    groupedMsgs = new ArrayList<>();
                }
                groupedMsgs.add(msg);
                continue;
            }
            if (!groupedMsgs.isEmpty()) {
                enqueueAlbum(queue, currentAccount, groupedMsgs, key, reply == 0 ? topicId : reply, parentFragment, consumeReplaceText(saveOriginalCaptions, replaceText), notify, monoForumPeerId, deque, replyInput);
                groupedMsgs = new ArrayList<>();
            }
            final MessageObject copyMsg = msg;
            final String copyText = consumeReplaceText(saveOriginalCaptions, replaceText);
            queue.add(() -> {
                SendMessagesHelper.getInstance(currentAccount).processForwardFromMyName(copyMsg, key, 0, monoForumPeerId, null, copyText, notify, topicId);
                deque[0].run();
            });
        }
        if (!groupedMsgs.isEmpty()) {
            enqueueAlbum(queue, currentAccount, groupedMsgs, key, reply == 0 ? topicId : reply, parentFragment, consumeReplaceText(saveOriginalCaptions, replaceText), notify, monoForumPeerId, deque, replyInput);
        }
        deque[0].run();
    }

    private static String consumeReplaceText(boolean saveOriginalCaptions, String[] replaceText) {
        String temp = replaceText[0];
        replaceText[0] = "";
        return saveOriginalCaptions ? null : temp;
    }

    private static void enqueueAlbum(
            ArrayList<Runnable> queue,
            int currentAccount,
            ArrayList<MessageObject> groupedMsgs,
            long peer,
            int reply,
            BaseFragment parentFragment,
            String copyText,
            boolean notify,
            long monoForumPeerId,
            Runnable[] deque,
            TLRPC.InputReplyTo replyInput) {
        final ArrayList<MessageObject> copyGrouped = new ArrayList<>(groupedMsgs);
        queue.add(() -> sendItemsAsAlbum(currentAccount, copyGrouped, peer, reply, parentFragment, copyText, notify, monoForumPeerId, deque[0], replyInput));
    }

    public static void groupItemsIntoAlbum(
            long key,
            int reply,
            String text,
            ArrayList<MessageObject> sendingMessageObjects,
            int currentAccount,
            BaseFragment parentFragment,
            boolean notify,
            long monoForumPeerId,
            TLRPC.InputReplyTo replyInput) {
        if (sendingMessageObjects == null || sendingMessageObjects.isEmpty()) {
            return;
        }
        int split = Math.min(10, sendingMessageObjects.size());
        ArrayList<MessageObject> objectsToSend = new ArrayList<>(sendingMessageObjects.subList(0, split));
        ArrayList<MessageObject> objectsToDelay = new ArrayList<>(sendingMessageObjects.subList(split, sendingMessageObjects.size()));
        Runnable finish = () -> groupItemsIntoAlbum(key, reply, text, objectsToDelay, currentAccount, parentFragment, notify, monoForumPeerId, replyInput);
        sendItemsAsAlbum(currentAccount, objectsToSend, key, reply, parentFragment, text, notify, monoForumPeerId, finish, replyInput);
    }

    public static TLRPC.InputMedia inputMediaFromMessageObject(MessageObject m) {
        TLRPC.MessageMedia sourceMedia = m.messageOwner != null ? m.messageOwner.media : null;
        if (sourceMedia == null
                || sourceMedia instanceof TLRPC.TL_messageMediaEmpty
                || sourceMedia instanceof TLRPC.TL_messageMediaWebPage
                || sourceMedia instanceof TLRPC.TL_messageMediaGame
                || sourceMedia instanceof TLRPC.TL_messageMediaInvoice) {
            return new TLRPC.TL_inputMediaEmpty();
        }
        if (hasDocument(m)) {
            TLRPC.Document document = sourceMedia.document;
            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
            media.id = new TLRPC.TL_inputDocument();
            media.id.id = document.id;
            media.id.access_hash = document.access_hash;
            media.id.file_reference = document.file_reference != null ? document.file_reference : new byte[0];
            return media;
        }
        if (hasPhoto(m)) {
            TLRPC.Photo photo = sourceMedia.photo;
            TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
            media.id = new TLRPC.TL_inputPhoto();
            media.id.id = photo.id;
            media.id.access_hash = photo.access_hash;
            media.id.file_reference = photo.file_reference != null ? photo.file_reference : new byte[0];
            return media;
        }
        return new TLRPC.TL_inputMediaEmpty();
    }

    public static void sendItemsAsAlbum(
            int currentAccount,
            ArrayList<MessageObject> messages,
            long peer,
            int reply,
            BaseFragment fragment,
            String replaceText,
            boolean notify,
            long monoForumPeerId,
            Runnable finish,
            TLRPC.InputReplyTo replyInput) {
        if (peer == 0L || messages == null || messages.isEmpty() || messages.size() > 10) {
            return;
        }
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        long lowerId = monoForumPeerId != 0L ? monoForumPeerId : peer;
        TLRPC.InputPeer sendToPeer = accountInstance.getMessagesController().getInputPeer(lowerId);
        if (sendToPeer == null) {
            return;
        }
        TLRPC.TL_messages_sendMultiMedia request = new TLRPC.TL_messages_sendMultiMedia();
        request.peer = sendToPeer;
        request.silent = !notify;
        request.reply_to = replyInput != null ? replyInput : SendMessagesHelper.getInstance(currentAccount).createReplyInput(reply);
        if (replyInput != null || reply != 0) {
            request.flags += 1;
        }

        for (int i = 0; i < messages.size(); i++) {
            MessageObject m = messages.get(i);
            TLRPC.InputMedia media = inputMediaFromMessageObject(m);
            if (media instanceof TLRPC.TL_inputMediaEmpty) {
                continue;
            }
            TLRPC.TL_inputSingleMedia inputSingleMedia = new TLRPC.TL_inputSingleMedia();
            inputSingleMedia.random_id = Utilities.random.nextLong();
            inputSingleMedia.media = media;
            if (replaceText == null) {
                inputSingleMedia.message = m.messageOwner.message;
                ArrayList<TLRPC.MessageEntity> entities = m.messageOwner.entities;
                if (entities != null && !entities.isEmpty()) {
                    inputSingleMedia.entities = entities;
                    inputSingleMedia.flags = inputSingleMedia.flags | 1;
                }
            } else {
                inputSingleMedia.message = request.multi_media.isEmpty() ? replaceText : "";
            }
            request.multi_media.add(inputSingleMedia);
        }

        accountInstance.getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                if (finish != null) {
                    AndroidUtilities.runOnUIThread(finish);
                }
                return;
            }
            if (!FileRefController.isFileRefError(error.text)) {
                showToast("It seems that you want to group incompatible file types.");
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, request));
                return;
            }
            BalalApi.tlrpcMessages(currentAccount, messages, (cloudMessages, msgErr) -> {
                if (cloudMessages == null || cloudMessages.isEmpty()) {
                    return;
                }
                boolean atLeastOneFileRefUpdated = false;
                int n = Math.min(cloudMessages.size(), messages.size());
                for (int i = 0; i < n; i++) {
                    TLRPC.MessageMedia cloudMedia = cloudMessages.get(i).media;
                    TLRPC.MessageMedia localMedia = messages.get(i).messageOwner.media;
                    if (cloudMedia == null || localMedia == null) {
                        continue;
                    }
                    if (cloudMedia.document != null && hasDocument(messages.get(i)) && localMedia.document != null) {
                        atLeastOneFileRefUpdated = true;
                        localMedia.document.file_reference = cloudMedia.document.file_reference;
                    }
                    if (cloudMedia.photo != null && hasPhoto(messages.get(i)) && localMedia.photo != null) {
                        atLeastOneFileRefUpdated = true;
                        localMedia.photo.file_reference = cloudMedia.photo.file_reference;
                    }
                }
                if (!atLeastOneFileRefUpdated) {
                    showToast("Sorry, something went wrong.");
                    return;
                }
                sendItemsAsAlbum(currentAccount, messages, peer, reply, fragment, replaceText, notify, monoForumPeerId, finish, replyInput);
            });
        });
    }

    private static void showToast(String msg) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, msg, Toast.LENGTH_LONG).show());
    }
}
