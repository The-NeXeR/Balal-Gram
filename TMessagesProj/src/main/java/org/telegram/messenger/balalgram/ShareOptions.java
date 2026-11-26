package org.telegram.messenger.balalgram;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.IntConsumer;

/**
 * Extra per-forward options in ShareAlert: send silently and delete originals after send.
 */
public class ShareOptions {

    private static final long SENT_TIMEOUT = 60000L;

    private final int currentAccount;
    private final int chatMode;
    private final int topicId;
    private final ArrayList<MessageObject> deletableMessages = new ArrayList<>();
    private final boolean hasMessages;

    private boolean sendWithoutSound = false;
    private boolean deleteOriginals = false;

    public ShareOptions(int currentAccount, ArrayList<MessageObject> messages, ChatActivity fragment) {
        this.currentAccount = currentAccount;
        this.chatMode = fragment != null ? fragment.getChatMode() : ChatActivity.MODE_DEFAULT;
        this.topicId = fragment != null ? (int) fragment.getTopicId() : 0;
        this.hasMessages = messages != null && !messages.isEmpty();
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                MessageObject message = messages.get(i);
                if (canDelete(message)) {
                    deletableMessages.add(message);
                }
            }
        }
    }

    public boolean isEmpty() {
        return !hasMessages;
    }

    public boolean isSendWithoutSound() {
        return sendWithoutSound;
    }

    public boolean isDeleteEnabled() {
        return deleteOriginals && !deletableMessages.isEmpty();
    }

    public void showMenu(ViewGroup container, Theme.ResourcesProvider resourcesProvider, View anchor) {
        ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, anchor);
        options.setDismissWithButtons(false);
        options.setGravity(Gravity.RIGHT);

        ActionBarMenuSubItem soundItem = addCheckbox(options, LocaleController.getString(R.string.SendWithoutSound), sendWithoutSound);
        soundItem.setOnClickListener(v -> {
            sendWithoutSound = !sendWithoutSound;
            soundItem.setChecked(sendWithoutSound);
        });

        if (!deletableMessages.isEmpty()) {
            ActionBarMenuSubItem deleteItem = addCheckbox(options, LocaleController.getString(R.string.ShareDeleteOriginals), deleteOriginals);
            deleteItem.setOnClickListener(v -> {
                deleteOriginals = !deleteOriginals;
                deleteItem.setChecked(deleteOriginals);
            });
        }

        options.show();
    }

    private ActionBarMenuSubItem addCheckbox(ItemOptions options, CharSequence text, boolean checked) {
        ActionBarMenuSubItem item = options.addChecked();
        item.setText(text);
        if (item.checkView != null) {
            item.checkView.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            item.checkView.setDrawUnchecked(true);
            item.checkView.setDrawBackgroundAsArc(10);
        }
        item.setChecked(checked);
        return item;
    }

    public void deleteOriginalsWhenSent(ArrayList<Long> targetDialogIds, int expectedMessagesPerDialog, IntConsumer onDeleted) {
        if (!isDeleteEnabled() || targetDialogIds == null || targetDialogIds.isEmpty() || expectedMessagesPerDialog <= 0) {
            return;
        }
        new SentWatcher(targetDialogIds, expectedMessagesPerDialog * targetDialogIds.size(), onDeleted).arm();
    }

    private final class SentWatcher implements NotificationCenter.NotificationCenterDelegate {

        private final HashSet<Long> dialogIds;
        private int pending;
        private final IntConsumer onDeleted;
        private final Runnable giveUp = this::disarm;

        SentWatcher(ArrayList<Long> targetDialogIds, int pending, IntConsumer onDeleted) {
            this.dialogIds = new HashSet<>(targetDialogIds);
            this.pending = pending;
            this.onDeleted = onDeleted;
        }

        void arm() {
            NotificationCenter center = NotificationCenter.getInstance(currentAccount);
            center.addObserver(this, NotificationCenter.messageReceivedByServer);
            center.addObserver(this, NotificationCenter.messageSendError);
            AndroidUtilities.runOnUIThread(giveUp, SENT_TIMEOUT);
        }

        private void disarm() {
            AndroidUtilities.cancelRunOnUIThread(giveUp);
            NotificationCenter center = NotificationCenter.getInstance(currentAccount);
            center.removeObserver(this, NotificationCenter.messageReceivedByServer);
            center.removeObserver(this, NotificationCenter.messageSendError);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.messageSendError) {
                disarm();
                return;
            }
            if (id != NotificationCenter.messageReceivedByServer || args == null || args.length < 7) {
                return;
            }
            if (Boolean.TRUE.equals(args[6]) || !(args[3] instanceof Long)) {
                return;
            }
            Long dialogId = (Long) args[3];
            if (!dialogIds.contains(dialogId)) {
                return;
            }
            pending--;
            if (pending > 0) {
                return;
            }
            disarm();
            int deleted = deleteNow();
            if (deleted > 0 && onDeleted != null) {
                onDeleted.accept(deleted);
            }
        }
    }

    private int deleteNow() {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        LongSparseArray<ArrayList<MessageObject>> revokable = new LongSparseArray<>();
        LongSparseArray<ArrayList<MessageObject>> localOnly = new LongSparseArray<>();
        int count = 0;
        for (int i = 0; i < deletableMessages.size(); i++) {
            MessageObject message = deletableMessages.get(i);
            long dialogId = message.getDialogId();
            if (message.isEphemeral()) {
                controller.deleteEphemeralMessage(dialogId, topicId, message);
                count++;
                continue;
            }
            LongSparseArray<ArrayList<MessageObject>> target = canRevoke(message) ? revokable : localOnly;
            ArrayList<MessageObject> group = target.get(dialogId);
            if (group == null) {
                group = new ArrayList<>();
                target.put(dialogId, group);
            }
            group.add(message);
            count++;
        }
        deleteGroups(revokable, true);
        deleteGroups(localOnly, false);
        deletableMessages.clear();
        return count;
    }

    private void deleteGroups(LongSparseArray<ArrayList<MessageObject>> groups, boolean forAll) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (int a = 0; a < groups.size(); a++) {
            long dialogId = groups.keyAt(a);
            TLRPC.EncryptedChat encryptedChat = DialogObject.isEncryptedDialog(dialogId)
                    ? controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
                    : null;
            ArrayList<Integer> ids = new ArrayList<>();
            ArrayList<Long> randomIds = null;
            ArrayList<MessageObject> list = groups.valueAt(a);
            for (int i = 0; i < list.size(); i++) {
                MessageObject message = list.get(i);
                ids.add(message.getId());
                if (encryptedChat != null && message.messageOwner.random_id != 0L && message.type != MessageObject.TYPE_DATE) {
                    if (randomIds == null) {
                        randomIds = new ArrayList<>();
                    }
                    randomIds.add(message.messageOwner.random_id);
                }
            }
            controller.deleteMessages(ids, randomIds, encryptedChat, dialogId, topicId, forAll, chatMode);
        }
    }

    private boolean canDelete(MessageObject message) {
        return message != null && message.canDeleteMessage(chatMode == ChatActivity.MODE_SCHEDULED, chatOf(message));
    }

    private boolean canRevoke(MessageObject message) {
        if (chatMode != ChatActivity.MODE_DEFAULT) {
            return false;
        }
        long dialogId = message.getDialogId();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return true;
        }
        TLRPC.Chat chat = chatOf(message);
        if (ChatObject.isChannel(chat)) {
            return true;
        }
        MessagesController controller = MessagesController.getInstance(currentAccount);
        int age = ConnectionsManager.getInstance(currentAccount).getCurrentTime() - message.messageOwner.date;
        if (chat != null) {
            return (message.isOut() || ChatObject.canBlockUsers(chat)) && age <= controller.revokeTimeLimit;
        }
        TLRPC.User user = controller.getUser(dialogId);
        if (user == null || UserObject.isUserSelf(user) || UserObject.isDeleted(user) || (user.bot && !user.support)) {
            return false;
        }
        return (message.isOut() || controller.canRevokePmInbox) && age <= controller.revokeTimePmLimit;
    }

    private TLRPC.Chat chatOf(MessageObject message) {
        long dialogId = message.getDialogId();
        if (DialogObject.isChatDialog(dialogId)) {
            return MessagesController.getInstance(currentAccount).getChat(-dialogId);
        }
        return null;
    }
}
