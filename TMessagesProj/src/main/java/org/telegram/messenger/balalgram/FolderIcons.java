package org.telegram.messenger.balalgram;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.HashMap;

/**
 * Folder emoticon mappings and tab style rendering helpers.
 */
public final class FolderIcons {

    public static final int FOLDER_TABS_STYLE_TEXT = 0;
    public static final int FOLDER_TABS_STYLE_ICON_TEXT = 1;
    public static final int FOLDER_TABS_STYLE_ICON = 2;

    public static final int FLAG_EMOTICON = 1 << 25;

    public static final String PRIVATE = "\uD83D\uDC64";
    public static final String GROUP = "\uD83D\uDC65";
    public static final String CHANNELS = "\uD83D\uDCE2";
    public static final String BOTS = "\uD83E\uDD16";
    public static final String UNREAD = "\u2705";
    public static final String UNMUTED = "\uD83D\uDD14";
    public static final String ALL = "\uD83D\uDCAC";
    public static final String CUSTOM = "\uD83D\uDCC1";

    public static final String[] EMOTICONS = new String[]{
            ALL,
            CUSTOM,
            PRIVATE,
            GROUP,
            CHANNELS,
            BOTS,
            "\u2B50",
            UNREAD,
            UNMUTED,
            "\uD83D\uDC31",
            "\uD83D\uDCD5",
            "\uD83D\uDCB0",
            "\uD83C\uDFAE",
            "\uD83D\uDCA1",
            "\uD83D\uDC4C",
            "\uD83C\uDFB5",
            "\uD83C\uDFA8",
            "\u2708\uFE0F",
            "\u26BD\uFE0F",
            "\uD83C\uDF93",
            "\uD83D\uDEEB",
            "\uD83D\uDC51",
            "\uD83C\uDF39",
            "\uD83C\uDFE0",
            "\u2764",
            "\uD83C\uDFAD",
            "\uD83C\uDF78",
            "\uD83D\uDCC8",
            "\uD83D\uDCBC",
            "\uD83D\uDCCB",
    };

    private static final int[] ICON_RES = new int[]{
            R.drawable.folder_all,
            R.drawable.folder_custom,
            R.drawable.folder_private,
            R.drawable.folder_group,
            R.drawable.folder_channels,
            R.drawable.folder_bots,
            R.drawable.folder_favorite,
            R.drawable.folder_unread,
            R.drawable.folder_unmuted,
            R.drawable.folder_cat,
            R.drawable.folder_book,
            R.drawable.folder_money,
            R.drawable.folder_game,
            R.drawable.folder_light,
            R.drawable.folder_like,
            R.drawable.folder_note,
            R.drawable.folder_palette,
            R.drawable.folder_travel,
            R.drawable.folder_sport,
            R.drawable.folder_study,
            R.drawable.folder_airplane,
            R.drawable.folder_crown,
            R.drawable.folder_flower,
            R.drawable.folder_home,
            R.drawable.folder_love,
            R.drawable.folder_mask,
            R.drawable.folder_party,
            R.drawable.folder_trade,
            R.drawable.folder_work,
            R.drawable.folder_setup,
    };

    private static final HashMap<String, Integer> BY_EMOTICON = new HashMap<>();

    static {
        for (int i = 0; i < EMOTICONS.length; i++) {
            BY_EMOTICON.put(normalize(EMOTICONS[i]), ICON_RES[i]);
        }
    }

    private FolderIcons() {
    }

    public static int folderTabsStyle() {
        return MessagesController.getGlobalMainSettings().getInt("folderTabsStyle", FOLDER_TABS_STYLE_TEXT);
    }

    public static String wireEmoticon(TLRPC.DialogFilter newFilter) {
        if (newFilter == null) {
            return null;
        }
        return (newFilter.flags & FLAG_EMOTICON) != 0 ? newFilter.emoticon : null;
    }

    private static String normalize(String emoticon) {
        if (emoticon == null) {
            return "";
        }
        return emoticon.replace("\uFE0F", "").replace("\uFE0E", "");
    }

    @DrawableRes
    public static int getIconResByEmoticon(String emoticon) {
        if (TextUtils.isEmpty(emoticon)) {
            return 0;
        }
        Integer res = BY_EMOTICON.get(normalize(emoticon));
        return res != null ? res : 0;
    }

    public static String computeDefaultEmoticon(MessagesController.DialogFilter filter) {
        if (filter == null || filter.isDefault()) {
            return ALL;
        }
        return computeDefaultEmoticon(filter.flags, !filter.alwaysShow.isEmpty(), !filter.neverShow.isEmpty());
    }

    public static String computeDefaultEmoticon(int flags, boolean hasAlwaysShow, boolean hasNeverShow) {
        int all = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        if (hasAlwaysShow || hasNeverShow || (flags & all) == 0) {
            return CUSTOM;
        }
        int contacts = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
        int nonContacts = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
        int masked = flags & all;
        if (masked == contacts || masked == nonContacts || masked == (contacts | nonContacts)) {
            return PRIVATE;
        } else if (masked == MessagesController.DIALOG_FILTER_FLAG_GROUPS) {
            return GROUP;
        } else if (masked == MessagesController.DIALOG_FILTER_FLAG_CHANNELS) {
            return CHANNELS;
        } else if (masked == MessagesController.DIALOG_FILTER_FLAG_BOTS) {
            return BOTS;
        }
        int excludeRead = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
        int excludeMuted = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
        int readMuted = flags & (excludeRead | excludeMuted);
        if (readMuted == excludeRead) {
            return UNREAD;
        } else if (readMuted == excludeMuted) {
            return UNMUTED;
        }
        return CUSTOM;
    }

    @DrawableRes
    public static int getIconRes(MessagesController.DialogFilter filter) {
        if (filter == null) {
            return 0;
        }
        int res = getIconResByEmoticon(filter.emoticon);
        if (res != 0) {
            return res;
        }
        return getIconResByEmoticon(computeDefaultEmoticon(filter));
    }

    public static CharSequence applyIcon(CharSequence name, int iconRes) {
        int style = folderTabsStyle();
        if (iconRes == 0 || style == FOLDER_TABS_STYLE_TEXT) {
            return name;
        }
        SpannableStringBuilder result = new SpannableStringBuilder(" ");
        ColoredImageSpan span = new ColoredImageSpan(iconRes, ColoredImageSpan.ALIGN_CENTER);
        span.setSize(AndroidUtilities.dp(18));
        result.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (style == FOLDER_TABS_STYLE_ICON_TEXT && !TextUtils.isEmpty(name)) {
            result.append("  ");
            result.append(name);
        }
        return result;
    }
}
