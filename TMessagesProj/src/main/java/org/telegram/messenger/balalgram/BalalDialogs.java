package org.telegram.messenger.balalgram;

import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Dialog builders for BalalGram bulk-delete confirmations and text input prompts.
 */
public final class BalalDialogs {

    private BalalDialogs() {
    }

    public static void createVoiceCaptionAlert(
            Context context,
            ArrayList<String> timestamps,
            Utilities.Callback<String> finish
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.Caption));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.HORIZONTAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setBackground(Theme.createEditTextDrawable(context, true));
        editText.setSingleLine(false);
        editText.setFocusable(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.requestFocus();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < timestamps.size(); i++) {
            sb.append(i + 1).append(". ").append(timestamps.get(i)).append(" \n");
        }
        editText.setText(sb.toString());
        editText.setPadding(0, 0, 0, 0);

        textLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
        builder.setView(textLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.Send),
                (dialog, which) -> finish.run(editText.getText().toString()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show().setOnShowListener(dialog -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
        }
        layoutParams.leftMargin = AndroidUtilities.dp(24);
        layoutParams.rightMargin = layoutParams.leftMargin;
        layoutParams.height = AndroidUtilities.dp(36 * 3);
        editText.setLayoutParams(layoutParams);
    }

    private static void showRedConfirmDialog(Context context, String title, String message, Runnable onConfirm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(AndroidUtilities.replaceTags(message));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> onConfirm.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.show();
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private static void deleteMessages(MessagesController messagesController, long dialogId, ArrayList<TLRPC.Message> found, boolean skipPinnedAndService) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (TLRPC.Message msg : found) {
            if (skipPinnedAndService && (msg.pinned || msg instanceof TLRPC.TL_messageService)) {
                continue;
            }
            ids.add(msg.id);
        }
        if (ids.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() ->
                messagesController.deleteMessages(ids, null, null, dialogId, 0, true, 0));
    }

    public static void createDeleteAllYourMessagesAlert(int currentAccount, long dialogId, Context context) {
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        long meId = UserConfig.getInstance(currentAccount).getClientUserId();
        String title = LocaleController.getString(R.string.DeleteAllYourMessages);

        showRedConfirmDialog(context, title, LocaleController.getString(R.string.DeleteAllYourMessagesInfo), () ->
                showRedConfirmDialog(context, title, LocaleController.getString(R.string.ReallySure), () -> {
                    TLRPC.InputPeer dialogPeer = messagesController.getInputPeer(dialogId);
                    TLRPC.InputPeer mePeer = messagesController.getInputPeer(meId);
                    BalalApi.searchAllMessages(
                            currentAccount,
                            dialogPeer,
                            mePeer,
                            found -> deleteMessages(messagesController, dialogId, found, false),
                            () -> {
                                if (dialogPeer.channel_id != 0L) {
                                    BalalApi.fullChannel(currentAccount, dialogPeer.channel_id, full -> {
                                        long migratedFrom = full.full_chat.migrated_from_chat_id;
                                        if (migratedFrom == 0L) {
                                            return;
                                        }
                                        BalalApi.searchAllMessages(
                                                currentAccount,
                                                messagesController.getInputPeer(-migratedFrom),
                                                mePeer,
                                                found -> deleteMessages(messagesController, -migratedFrom, found, false),
                                                () -> {
                                                }
                                        );
                                    });
                                }
                            }
                    );
                }));
    }

    public static void createDeleteAllUnpinnedMessagesAlert(int currentAccount, long dialogId, Context context) {
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        String title = LocaleController.getString(R.string.DeleteAllUnpinnedMessages);

        showRedConfirmDialog(context, title, LocaleController.getString(R.string.DeleteAllUnpinnedMessagesInfo), () ->
                showRedConfirmDialog(context, title, LocaleController.getString(R.string.ReallySure), () -> {
                    TLRPC.InputPeer dialogPeer = messagesController.getInputPeer(dialogId);
                    TLRPC.InputPeer emptyPeer = new TLRPC.TL_inputPeerEmpty();
                    BalalApi.searchAllMessages(
                            currentAccount,
                            dialogPeer,
                            emptyPeer,
                            found -> deleteMessages(messagesController, dialogId, found, true),
                            () -> {
                                if (dialogPeer.channel_id != 0L) {
                                    BalalApi.fullChannel(currentAccount, dialogPeer.channel_id, full -> {
                                        long migratedFrom = full.full_chat.migrated_from_chat_id;
                                        if (migratedFrom == 0L) {
                                            return;
                                        }
                                        BalalApi.searchAllMessages(
                                                currentAccount,
                                                messagesController.getInputPeer(-migratedFrom),
                                                emptyPeer,
                                                found -> deleteMessages(messagesController, -migratedFrom, found, true),
                                                () -> {
                                                }
                                        );
                                    });
                                }
                            }
                    );
                }));
    }

    public static void createDeleteAllYourMessagesInAllTopicsAlert(int currentAccount, long dialogId, Context context) {
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        TopicsController topicsController = messagesController.getTopicsController();
        long meId = UserConfig.getInstance(currentAccount).getClientUserId();
        long chatId = -dialogId;
        String title = LocaleController.getString(R.string.DeleteAllYourMessages);
        String warning = LocaleController.getString(R.string.DeleteAllYourMessagesInfo)
                + "\n\nThis will delete your messages in ALL topics of this group.";

        showRedConfirmDialog(context, title, warning, () ->
                showRedConfirmDialog(context, title, LocaleController.getString(R.string.ReallySure), () -> {
                    ArrayList<TLRPC.TL_forumTopic> topics = topicsController.getTopics(chatId);
                    TLRPC.InputPeer mePeer = messagesController.getInputPeer(meId);
                    TLRPC.InputPeer dialogPeer = messagesController.getInputPeer(dialogId);
                    if (topics != null && !topics.isEmpty()) {
                        for (final TLRPC.TL_forumTopic topic : topics) {
                            BalalApi.searchAllMessages(
                                    currentAccount,
                                    dialogPeer,
                                    mePeer,
                                    found -> {
                                        ArrayList<TLRPC.Message> topicMessages = new ArrayList<>();
                                        for (TLRPC.Message message : found) {
                                            int topicId;
                                            if (message.reply_to != null && message.reply_to.reply_to_top_id != 0) {
                                                topicId = message.reply_to.reply_to_top_id;
                                            } else if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0) {
                                                topicId = message.reply_to.reply_to_msg_id;
                                            } else {
                                                topicId = message.id;
                                            }
                                            if (topicId == topic.id) {
                                                topicMessages.add(message);
                                            }
                                        }
                                        if (!topicMessages.isEmpty()) {
                                            deleteMessages(messagesController, dialogId, topicMessages, false);
                                        }
                                    },
                                    () -> {
                                    }
                            );
                        }
                    } else {
                        BalalApi.searchAllMessages(
                                currentAccount,
                                dialogPeer,
                                mePeer,
                                found -> deleteMessages(messagesController, dialogId, found, false),
                                () -> {
                                }
                        );
                    }
                }));
    }

    public static void createFieldAlert(Context context, String title, String defaultValue, Utilities.Callback<String> finish) {
        createFieldAlert(context, title, defaultValue, finish, null);
    }

    public static void createFieldAlert(
            Context context,
            String title,
            String defaultValue,
            Utilities.Callback<String> finish,
            String message
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        if (message != null) {
            builder.setMessage(message);
        }

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.HORIZONTAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setBackground(Theme.createEditTextDrawable(context, true));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setText(defaultValue);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);

        textLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
        builder.setView(textLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK),
                (dialog, which) -> finish.run(editText.getText().toString().trim()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show().setOnShowListener(dialog -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
        }
        layoutParams.leftMargin = AndroidUtilities.dp(24);
        layoutParams.rightMargin = layoutParams.leftMargin;
        editText.setLayoutParams(layoutParams);
    }
}
