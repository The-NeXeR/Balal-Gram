package org.telegram.messenger.balalgram;

import android.app.Activity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Shows an optional confirmation dialog before sending a sticker or GIF.
 */
public final class MediaSendConfirmation {

    private MediaSendConfirmation() {
    }

    public static void confirm(BaseFragment fragment, boolean sticker, Runnable send) {
        if (fragment == null || !MessagesController.getGlobalMainSettings().getBoolean("confirmGifStickerSend", false)) {
            send.run();
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity, fragment.getResourceProvider())
                .setTitle(LocaleController.getString(sticker ? R.string.ConfirmSendSticker : R.string.ConfirmSendGif))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Send), (d, which) -> send.run())
                .create();
        fragment.showDialog(dialog);
    }
}
