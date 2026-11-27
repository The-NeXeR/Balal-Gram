package org.telegram.messenger.balalgram;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Provides an action-bar avatar account switcher menu item and popup.
 */
public final class AccountSelector {

    private interface MenuAdder {
        ActionBarMenuItem add(int icon, int width);
    }

    private AccountSelector() {
    }

    public static boolean hasAccountsToSwitch(int currentAccount) {
        ArrayList<Integer> accounts = new ArrayList<>();
        AccountOrder.collectAccountNumbers(accounts, currentAccount);
        return !accounts.isEmpty();
    }

    public static ActionBarMenuItem addToMenu(
            BaseFragment fragment,
            ActionBarMenu menu,
            int id,
            Utilities.Callback<Integer> onSelected
    ) {
        if (menu == null || !hasAccountsToSwitch(fragment.getCurrentAccount())) {
            return null;
        }
        return addToMenu(fragment, onSelected, (icon, width) -> menu.addItemWithWidth(id, icon, width));
    }

    public static ActionBarMenuItem addToMenu(
            BaseFragment fragment,
            SimpleActionBarMenu menu,
            int id,
            Utilities.Callback<Integer> onSelected
    ) {
        if (menu == null || !hasAccountsToSwitch(fragment.getCurrentAccount())) {
            return null;
        }
        return addToMenu(fragment, onSelected, (icon, width) -> menu.addItemWithWidth(id, icon, width));
    }

    private static ActionBarMenuItem addToMenu(
            BaseFragment fragment,
            Utilities.Callback<Integer> onSelected,
            MenuAdder adder
    ) {
        final int currentAccount = fragment.getCurrentAccount();
        final ActionBarMenuItem item = adder.add(0, AndroidUtilities.dp(56));

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        avatarDrawable.setInfo(currentAccount, user);

        BackupImageView imageView = new BackupImageView(item.getContext());
        imageView.setRoundRadius(AndroidUtilities.dp(18));
        imageView.getImageReceiver().setCurrentAccount(currentAccount);
        Drawable thumb = user != null && user.photo != null && user.photo.strippedBitmap != null
                ? user.photo.strippedBitmap
                : avatarDrawable;
        imageView.setImage(
                ImageLocation.getForUserOrChat(currentAccount, user, ImageLocation.TYPE_SMALL), "50_50",
                ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50",
                thumb, user
        );
        item.addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        final View.OnTouchListener[] dragOpenHolder = new View.OnTouchListener[1];
        final Runnable rearm = () -> AndroidUtilities.runOnUIThread(() -> item.setOnTouchListener(dragOpenHolder[0]));
        dragOpenHolder[0] = (view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getY() > view.getHeight()) {
                view.cancelLongPress();
                showPopup(fragment, view, onSelected, rearm);
                return true;
            }
            return false;
        };
        item.setOnTouchListener(dragOpenHolder[0]);
        item.setOnClickListener(view -> showPopup(fragment, view, onSelected, rearm));
        item.setOnLongClickListener(view -> {
            showPopup(fragment, view, onSelected, rearm);
            return true;
        });
        return item;
    }

    public static void showPopup(BaseFragment fragment, View anchor, Utilities.Callback<Integer> onSelected) {
        showPopup(fragment, anchor, onSelected, null);
    }

    private static void showPopup(
            BaseFragment fragment,
            View anchor,
            Utilities.Callback<Integer> onSelected,
            Runnable onDismiss
    ) {
        final int currentAccount = fragment.getCurrentAccount();
        ArrayList<Integer> accounts = new ArrayList<>();
        AccountOrder.collectAccountNumbers(accounts);
        if (!accounts.contains(currentAccount)) {
            accounts.add(0, currentAccount);
        }

        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        options.setMinWidth(230);
        for (final int account : accounts) {
            options.addAccount(account, account == currentAccount, () -> {
                options.dismiss();
                if (account != currentAccount) {
                    onSelected.run(account);
                }
            });
        }

        Drawable background = Theme.createRoundRectDrawable(
                AndroidUtilities.dp(24),
                fragment.getThemedColor(Theme.key_windowBackgroundWhite)
        );
        if (background instanceof android.graphics.drawable.ShapeDrawable) {
            ((android.graphics.drawable.ShapeDrawable) background).getPaint().setShadowLayer(
                    AndroidUtilities.dp(6),
                    0f,
                    AndroidUtilities.dp(1),
                    Theme.multAlpha(fragment.getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.15f)
            );
        }
        options.setViewAdditionalOffsets(
                -AndroidUtilities.dp(4),
                -AndroidUtilities.dp(4),
                -AndroidUtilities.dp(4),
                -AndroidUtilities.dp(4)
        );
        options.setScrimViewBackground(background);
        options.translate(0f, -AndroidUtilities.dp(4));
        options.setGravity(Gravity.RIGHT);
        if (onDismiss != null) {
            options.setOnDismiss(onDismiss);
        }
        options.show();
    }
}
