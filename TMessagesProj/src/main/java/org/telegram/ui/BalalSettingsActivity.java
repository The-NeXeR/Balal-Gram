package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.pm.ShortcutManagerCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.balalgram.FolderIcons;
import org.telegram.messenger.balalgram.QuickRepeat;
import org.telegram.messenger.balalgram.SettingsBackup;
import org.telegram.messenger.balalgram.VoiceFileSize;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Searchable settings screen for BalalGram client customizations, media/voice options, and backup.
 */
public class BalalSettingsActivity extends BaseFragment {

    public static final int ID_HIDE_SENSITIVE_DATA = 1;
    public static final int ID_HIDE_SENSITIVE_PHONE = 2;
    public static final int ID_HIDE_SENSITIVE_USERNAME = 3;
    public static final int ID_HIDE_SENSITIVE_BIO = 4;
    public static final int ID_HIDE_SENSITIVE_ID = 5;
    public static final int ID_FORCE_BLOCK_SCREENSHOTS = 6;

    public static final int ID_SYNC_PINS = 10;
    public static final int ID_UNMUTED_ON_TOP = 11;
    public static final int ID_OPEN_ARCHIVE_ON_PULL = 12;
    public static final int ID_ENABLE_LAST_SEEN_DOTS = 13;
    public static final int ID_HIDE_STORIES_IN_ARCHIVE = 14;
    public static final int ID_DISABLE_THUMBS_IN_DIALOG_LIST = 15;
    public static final int ID_HIDE_CONTACTS_IN_DIALOGS = 16;
    public static final int ID_HIDE_ALL_CHATS_TAB = 17;
    public static final int ID_DEFAULT_FOLDER = 18;
    public static final int ID_FOLDER_TABS_STYLE = 19;

    public static final int ID_REPLACE_FORWARD = 20;
    public static final int ID_MENTION_BY_NAME = 21;
    public static final int ID_DISABLE_LINK_PREVIEW_BY_DEFAULT = 22;
    public static final int ID_DELETE_ALL_UNPINNED = 23;
    public static final int ID_DISABLE_SLIDE_TO_NEXT_CHANNEL = 24;
    public static final int ID_FORMATTING_MENU = 25;

    public static final int ID_DISABLE_QUICK_REACTION = 26;
    public static final int ID_HIDE_MESSAGE_REACTIONS = 27;
    public static final int ID_HIDE_SAVED_MESSAGES_TAGS = 28;
    public static final int ID_DISABLE_LOCKED_ANIMATED_EMOJI = 29;

    public static final int ID_INAPP_CAMERA = 30;
    public static final int ID_SYSTEM_CAMERA = 31;
    public static final int ID_PHOTO_HAS_STICKER = 32;
    public static final int ID_REAR_VIDEO_MESSAGES = 33;
    public static final int ID_DISABLE_FLIP_PHOTOS = 34;

    public static final int ID_FULL_RECENT_STICKERS = 35;
    public static final int ID_CONFIRM_GIF_STICKER_SEND = 36;
    public static final int ID_SHOW_ARCHIVED_STICKERS = 37;
    public static final int ID_STICKER_SIZE = 38;

    public static final int ID_WEBSOCKET_TRANSPORT = 40;
    public static final int ID_WEBSOCKET_DOMAIN = 41;

    public static final int ID_VOICE_QUALITY = 50;
    public static final int ID_DISABLE_AUTOPLAY_NEXT_VOICE = 51;
    public static final int ID_VOICE_FILE_SIZE = 52;

    public static final int ID_BOT_SKIP_SHARE = 60;
    public static final int ID_BOT_SKIP_FULLSCREEN = 61;
    public static final int ID_QUICK_REPEAT = 62;

    public static final int ID_DISABLE_TABLET_MODE = 70;
    public static final int ID_LOCK_PREMIUM = 71;
    public static final int ID_RESET_LAUNCHER_SHORTCUTS = 72;

    public static final int ID_EXPORT_SETTINGS = 80;
    public static final int ID_IMPORT_SETTINGS = 81;

    private static final int MENU_SEARCH = 100;
    private static final int REQUEST_EXPORT_SETTINGS = 7311;
    private static final int REQUEST_IMPORT_SETTINGS = 7312;

    private UniversalRecyclerView listView;
    private ActionBarMenuItem searchItem;
    private StickerSizeCell stickerSizeCell;
    private int highlightItemId = 0;
    private String searchQuery = "";

    private final class StickerSizeCell extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;
        private final int startStickerSize = 2;
        private final int endStickerSize = (int) ChatMessageCell.MAX_STICKER_SIZE;
        private final String option = "stickerSize";

        StickerSizeCell(Context context) {
            super(context);
            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    setStickerSize(startStickerSize + diff() * progress);
                    invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
            addView(
                    sizeBar,
                    LayoutHelper.createFrame(
                            LayoutHelper.MATCH_PARENT,
                            38,
                            Gravity.LEFT | Gravity.TOP,
                            9,
                            5,
                            43,
                            11));
        }

        private float diff() {
            return (float) (endStickerSize - startStickerSize);
        }

        private float stickerSize() {
            return prefs().getFloat(option, endStickerSize);
        }

        private void setStickerSize(float size) {
            prefs().edit().putFloat(option, size).apply();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(
                    String.valueOf(Math.round(stickerSize())),
                    getMeasuredWidth() - AndroidUtilities.dp(39),
                    AndroidUtilities.dp(28),
                    textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((stickerSize() - startStickerSize) / diff());
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }
    }

    /**
     * Requests the recycler view to scroll to and highlight the specified item id when opened.
     */
    public BalalSettingsActivity highlight(int itemId) {
        this.highlightItemId = itemId;
        return this;
    }

    /**
     * Re-scrolls and highlights the specified item id if the fragment is already visible.
     */
    public void rehighlight(int itemId) {
        highlight(itemId);
        applyHighlight();
    }

    /**
     * Returns the spoofed Mini-App platform configured for a specific bot on the given account.
     */
    public static String getBotPlatform(int currentAccount, long botId) {
        return MessagesController.getMainSettings(currentAccount).getString("bot_platform_" + botId, "android");
    }

    /**
     * Returns whether external links opened from a bot Mini-App should be copied instead of launched.
     */
    public static boolean getBotCopyLink(int currentAccount, long botId) {
        return MessagesController.getMainSettings(currentAccount).getBoolean("bot_copy_link_" + botId, false);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.BalalSettingsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searchQuery = "";
                        updateList(false);
                    }

                    @Override
                    public void onSearchCollapse() {
                        searchQuery = "";
                        updateList(true);
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText.getText() != null ? editText.getText().toString().trim() : "";
                        updateList(false);
                    }
                });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        applyHighlight();
        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            if (invoked) {
                actionBar.closeSearchField();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward) {
            applyHighlight();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateList(false);
    }

    private void applyHighlight() {
        if (highlightItemId == 0 || listView == null) {
            return;
        }
        final int targetId = highlightItemId;
        highlightItemId = 0;
        listView.highlightRow(() -> {
            int pos = listView.findPositionByItemId(targetId);
            if (pos >= 0 && listView.layoutManager != null) {
                listView.layoutManager.scrollToPositionWithOffset(pos, AndroidUtilities.dp(60));
            }
            return pos;
        });
    }

    private void updateList(boolean animated) {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private static boolean pref(String key, boolean defaultValue) {
        return prefs().getBoolean(key, defaultValue);
    }

    private boolean togglePref(String key, UItem item, View view) {
        boolean newValue = !item.checked;
        prefs().edit().putBoolean(key, newValue).apply();
        item.checked = newValue;
        setCellChecked(view, newValue);
        return newValue;
    }

    private static void setCellChecked(View view, boolean checked) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        } else if (view instanceof NotificationsCheckCell) {
            ((NotificationsCheckCell) view).setChecked(checked);
        }
    }

    private boolean isSearching() {
        return searchItem != null && searchItem.isSearchFieldVisible2() && !TextUtils.isEmpty(searchQuery.trim());
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (isSearching()) {
            fillSearchResults(items);
        } else {
            fillSettings(items, false);
        }
    }

    private void fillSearchResults(ArrayList<UItem> items) {
        ArrayList<UItem> all = new ArrayList<>();
        fillSettings(all, true);

        String[] tokens = searchQuery.trim().toLowerCase().split("\\s+");
        UItem pendingHeader = null;
        boolean anyFound = false;
        for (int i = 0; i < all.size(); i++) {
            UItem item = all.get(i);
            if (item.viewType == UniversalAdapter.VIEW_TYPE_HEADER) {
                pendingHeader = item;
                continue;
            }
            if (item.viewType == UniversalAdapter.VIEW_TYPE_SHADOW || item.id <= 0) {
                continue;
            }
            if (!matches(item, tokens)) {
                continue;
            }
            if (pendingHeader != null) {
                if (anyFound) {
                    items.add(UItem.asShadow(null));
                }
                items.add(pendingHeader);
                pendingHeader = null;
            }
            items.add(item);
            anyFound = true;
        }
        items.add(UItem.asShadow(null));
    }

    private static boolean matches(UItem item, String[] tokens) {
        StringBuilder sb = new StringBuilder();
        if (item.text != null) sb.append(item.text).append(' ');
        if (item.subtext != null) sb.append(item.subtext).append(' ');
        if (item.textValue != null) sb.append(item.textValue);

        String haystack = sb.toString().toLowerCase();
        String translit = LocaleController.getInstance().getTranslitString(haystack);
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (haystack.contains(token)) continue;
            if (translit != null && translit.contains(token)) continue;
            return false;
        }
        return true;
    }

    private static UItem checkItem(int id, int titleRes, boolean checked) {
        return UItem.asCheck(id, LocaleController.getString(titleRes)).setChecked(checked);
    }

    private static UItem buttonCheckItem(int id, int titleRes, int infoRes, boolean checked, boolean enabled) {
        return UItem.asButtonCheck(id, LocaleController.getString(titleRes), LocaleController.getString(infoRes))
                .setChecked(checked)
                .setEnabled(enabled);
    }

    private void fillSettings(ArrayList<UItem> items, boolean includeHiddenForSearch) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.PrivacyTitle)));
        items.add(UItem.asButtonCheck(ID_HIDE_SENSITIVE_DATA, LocaleController.getString(R.string.HideSensitiveData), LocaleController.getString(R.string.BalalRestartRequired))
                .setChecked(SharedConfig.hideSensitiveData()));
        if (SharedConfig.hideSensitiveData() || includeHiddenForSearch) {
            items.add(checkItem(ID_HIDE_SENSITIVE_PHONE, R.string.HideSensitivePhone, pref("hideSensitivePhone", true)));
            items.add(checkItem(ID_HIDE_SENSITIVE_USERNAME, R.string.HideSensitiveUsername, pref("hideSensitiveUsername", true)));
            items.add(checkItem(ID_HIDE_SENSITIVE_BIO, R.string.HideSensitiveBio, pref("hideSensitiveBio", true)));
            items.add(checkItem(ID_HIDE_SENSITIVE_ID, R.string.HideSensitiveId, pref("hideSensitiveId", true)));
        }
        items.add(buttonCheckItem(ID_FORCE_BLOCK_SCREENSHOTS, R.string.ForceBlockScreenshots, R.string.ForceBlockScreenshotsInfo, pref("forceBlockScreenshots", false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.ChatList)));
        items.add(buttonCheckItem(ID_SYNC_PINS, R.string.SyncPins, R.string.SyncPinsInfo, pref("syncPins", true), true));
        items.add(buttonCheckItem(ID_UNMUTED_ON_TOP, R.string.UnmutedOnTop, R.string.UnmutedOnTopInfo, pref("unmutedOnTop", false), true));
        items.add(buttonCheckItem(ID_OPEN_ARCHIVE_ON_PULL, R.string.OpenArchiveOnPull, R.string.OpenArchiveOnPullInfo, pref("openArchiveOnPull", true), true));
        items.add(buttonCheckItem(ID_HIDE_STORIES_IN_ARCHIVE, R.string.HideStoriesInArchive, R.string.HideStoriesInArchiveInfo, pref("hideStoriesInArchive", false), true));
        items.add(buttonCheckItem(ID_DISABLE_THUMBS_IN_DIALOG_LIST, R.string.DisableThumbsInDialogList, R.string.DisableThumbsInDialogListInfo, pref("disableThumbsInDialogList", false), true));
        items.add(buttonCheckItem(ID_HIDE_CONTACTS_IN_DIALOGS, R.string.HideContactsInDialogs, R.string.HideContactsInDialogsInfo, pref("hideContactsInDialogs", false), true));
        items.add(buttonCheckItem(ID_ENABLE_LAST_SEEN_DOTS, R.string.EnableLastSeenDots, R.string.EnableLastSeenDotsInfo, pref("enableLastSeenDots", true), true));
        items.add(buttonCheckItem(ID_HIDE_ALL_CHATS_TAB, R.string.HideAllChatsTab, R.string.HideAllChatsTabInfo, pref("hideAllChatsTab", false), true));
        items.add(UItem.asSettingsCell(ID_DEFAULT_FOLDER, LocaleController.getString(R.string.DefaultFolder), getDefaultFolderText()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.FolderTabsStyle)));
        UItem folderStyleSlide = UItem.asSlideView(
                new String[]{
                        LocaleController.getString(R.string.FolderTabsStyleText),
                        LocaleController.getString(R.string.FolderTabsStyleIconText),
                        LocaleController.getString(R.string.FolderTabsStyleIcon)
                },
                FolderIcons.folderTabsStyle(),
                index -> {
                    prefs().edit().putInt("folderTabsStyle", index).apply();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                }).setId(ID_FOLDER_TABS_STYLE);
        folderStyleSlide.text = LocaleController.getString(R.string.FolderTabsStyle);
        folderStyleSlide.subtext = LocaleController.getString(R.string.FolderTabsStyleInfo);
        items.add(folderStyleSlide);
        items.add(UItem.asShadow(LocaleController.getString(R.string.FolderTabsStyleInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.SettingsChat)));
        items.add(buttonCheckItem(ID_REPLACE_FORWARD, R.string.ReplaceForward, R.string.ReplaceForwardInfo, pref("replaceForward", true), true));
        items.add(buttonCheckItem(ID_MENTION_BY_NAME, R.string.MentionByName, R.string.MentionByNameInfo, pref("mentionByName", false), true));
        items.add(buttonCheckItem(ID_DISABLE_LINK_PREVIEW_BY_DEFAULT, R.string.DisableLinkPreviewByDefault, R.string.DisableLinkPreviewByDefaultInfo, pref("disableLinkPreviewByDefault", false), true));
        items.add(buttonCheckItem(ID_DELETE_ALL_UNPINNED, R.string.AddDeleteAllUnpinnedMessages, R.string.AddDeleteAllUnpinnedMessagesInfo, pref("addItemToDeleteAllUnpinnedMessages", false), true));
        items.add(buttonCheckItem(ID_DISABLE_SLIDE_TO_NEXT_CHANNEL, R.string.DisableSlideToNextChannel, R.string.DisableSlideToNextChannelInfo, pref("disableSlideToNextChannel", false), true));
        items.add(UItem.asSettingsCell(ID_FORMATTING_MENU, LocaleController.getString(R.string.FormattingMenu), null));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.Reactions)));
        items.add(buttonCheckItem(ID_DISABLE_QUICK_REACTION, R.string.DisableQuickReaction, R.string.DisableQuickReactionInfo, pref("disableQuickReaction", false), true));
        items.add(buttonCheckItem(ID_HIDE_MESSAGE_REACTIONS, R.string.HideMessageReactions, R.string.HideMessageReactionsInfo, pref("hideMessageReactions", false), true));
        items.add(buttonCheckItem(ID_HIDE_SAVED_MESSAGES_TAGS, R.string.HideSavedMessagesTags, R.string.HideSavedMessagesTagsInfo, pref("hideSavedMessagesTags", false), true));
        items.add(buttonCheckItem(ID_DISABLE_LOCKED_ANIMATED_EMOJI, R.string.DisableLockedAnimatedEmoji, R.string.DisableLockedAnimatedEmojiInfo, pref("disableLockedAnimatedEmoji", false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.StickersName)));
        items.add(buttonCheckItem(ID_FULL_RECENT_STICKERS, R.string.FullRecentStickers, R.string.FullRecentStickersInfo, pref("fullRecentStickers", true), true));
        items.add(buttonCheckItem(ID_CONFIRM_GIF_STICKER_SEND, R.string.ConfirmGifStickerSend, R.string.ConfirmGifStickerSendInfo, pref("confirmGifStickerSend", false), true));
        items.add(buttonCheckItem(ID_SHOW_ARCHIVED_STICKERS, R.string.ShowArchivedStickers, R.string.ShowArchivedStickersInfo, pref("showArchivedStickers", false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.StickerSize)));
        if (stickerSizeCell == null) {
            stickerSizeCell = new StickerSizeCell(getContext());
        }
        UItem stickerSizeItem = UItem.asCustom(ID_STICKER_SIZE, stickerSizeCell);
        stickerSizeItem.text = LocaleController.getString(R.string.StickerSize);
        items.add(stickerSizeItem);
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BalalSectionMedia)));
        items.add(buttonCheckItem(ID_INAPP_CAMERA, R.string.InAppCamera, R.string.InAppCameraInfo, SharedConfig.inappCamera, true));
        items.add(buttonCheckItem(ID_SYSTEM_CAMERA, R.string.SystemCamera, R.string.SystemCameraInfo, pref("systemCamera", false), SharedConfig.inappCamera));
        items.add(buttonCheckItem(ID_PHOTO_HAS_STICKER, R.string.PhotoHasSticker, R.string.PhotoHasStickerInfo, pref("photoHasSticker", true), true));
        items.add(buttonCheckItem(ID_DISABLE_FLIP_PHOTOS, R.string.DisableFlipPhotos, R.string.DisableFlipPhotosInfo, pref("disableFlipPhotos", false), true));
        items.add(buttonCheckItem(ID_REAR_VIDEO_MESSAGES, R.string.RearVideoMessages, R.string.RearVideoMessagesInfo, pref("rearVideoMessages", false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BalalSectionVoice)));
        items.add(UItem.asSettingsCell(ID_VOICE_QUALITY, LocaleController.getString(R.string.VoiceMessageQuality), getVoiceQualityText()));
        items.add(buttonCheckItem(ID_DISABLE_AUTOPLAY_NEXT_VOICE, R.string.DisableAutoplayNextVoice, R.string.DisableAutoplayNextVoiceInfo, pref("disableAutoplayNextVoice", false), true));
        items.add(buttonCheckItem(ID_VOICE_FILE_SIZE, R.string.VoiceFileSize, R.string.VoiceFileSizeInfo, pref(VoiceFileSize.PREF_ENABLED, false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BalalSectionBots)));
        items.add(buttonCheckItem(ID_BOT_SKIP_SHARE, R.string.BotSkipShare, R.string.BotSkipShareInfo, pref("botSkipShare", false), true));
        items.add(buttonCheckItem(ID_BOT_SKIP_FULLSCREEN, R.string.BotSkipFullscreen, R.string.BotSkipFullscreenInfo, pref("botSkipFullscreen", false), true));
        items.add(buttonCheckItem(ID_QUICK_REPEAT, R.string.QuickRepeatButton, R.string.QuickRepeatButtonInfo, pref(QuickRepeat.PREF_KEY, false), true));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.ProxySettings)));
        boolean wsEnabled = pref("webSocketTransport", false);
        items.add(buttonCheckItem(ID_WEBSOCKET_TRANSPORT, R.string.WebSocketTransport, R.string.WebSocketTransportInfo, wsEnabled, true));
        if (wsEnabled || includeHiddenForSearch) {
            items.add(UItem.asSettingsCell(ID_WEBSOCKET_DOMAIN, LocaleController.getString(R.string.WebSocketDomain), getWebSocketDomainText()));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BalalSectionSystem)));
        if (AndroidUtilities.isTabletInternal() || includeHiddenForSearch) {
            items.add(buttonCheckItem(ID_DISABLE_TABLET_MODE, R.string.DisableTabletMode, R.string.DisableTabletModeInfo, SharedConfig.forceDisableTabletMode, true));
        }
        items.add(buttonCheckItem(ID_LOCK_PREMIUM, R.string.LockPremium, R.string.LockPremiumInfo, pref("lockPremium", false), true));
        items.add(UItem.asSettingsCell(ID_RESET_LAUNCHER_SHORTCUTS, LocaleController.getString(R.string.ResetLauncherShortcuts), ""));
        items.add(UItem.asShadow(LocaleController.getString(R.string.ResetLauncherShortcutsInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.BalalSectionBackup)));
        items.add(UItem.asSettingsCell(ID_EXPORT_SETTINGS, LocaleController.getString(R.string.ExportSettings), ""));
        items.add(UItem.asSettingsCell(ID_IMPORT_SETTINGS, LocaleController.getString(R.string.ImportSettings), ""));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_HIDE_SENSITIVE_DATA:
                togglePref("hideSensitiveData", item, view);
                updateList(true);
                break;
            case ID_HIDE_SENSITIVE_PHONE:
                togglePref("hideSensitivePhone", item, view);
                break;
            case ID_HIDE_SENSITIVE_USERNAME:
                togglePref("hideSensitiveUsername", item, view);
                break;
            case ID_HIDE_SENSITIVE_BIO:
                togglePref("hideSensitiveBio", item, view);
                break;
            case ID_HIDE_SENSITIVE_ID:
                togglePref("hideSensitiveId", item, view);
                break;
            case ID_FORCE_BLOCK_SCREENSHOTS:
                togglePref("forceBlockScreenshots", item, view);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode, false);
                break;

            case ID_SYNC_PINS:
                togglePref("syncPins", item, view);
                break;
            case ID_UNMUTED_ON_TOP:
                togglePref("unmutedOnTop", item, view);
                MessagesController.getInstance(currentAccount).sortDialogs(null);
                break;
            case ID_OPEN_ARCHIVE_ON_PULL:
                togglePref("openArchiveOnPull", item, view);
                break;
            case ID_HIDE_STORIES_IN_ARCHIVE:
                togglePref("hideStoriesInArchive", item, view);
                break;
            case ID_DISABLE_THUMBS_IN_DIALOG_LIST:
                togglePref("disableThumbsInDialogList", item, view);
                break;
            case ID_HIDE_CONTACTS_IN_DIALOGS:
                togglePref("hideContactsInDialogs", item, view);
                break;
            case ID_ENABLE_LAST_SEEN_DOTS:
                togglePref("enableLastSeenDots", item, view);
                break;
            case ID_HIDE_ALL_CHATS_TAB:
                togglePref("hideAllChatsTab", item, view);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                break;
            case ID_DEFAULT_FOLDER:
                showDefaultFolderDialog();
                break;

            case ID_REPLACE_FORWARD:
                togglePref("replaceForward", item, view);
                break;
            case ID_MENTION_BY_NAME:
                togglePref("mentionByName", item, view);
                break;
            case ID_DISABLE_LINK_PREVIEW_BY_DEFAULT:
                togglePref("disableLinkPreviewByDefault", item, view);
                break;
            case ID_DELETE_ALL_UNPINNED:
                togglePref("addItemToDeleteAllUnpinnedMessages", item, view);
                break;
            case ID_DISABLE_SLIDE_TO_NEXT_CHANNEL:
                togglePref("disableSlideToNextChannel", item, view);
                break;
            case ID_FORMATTING_MENU:
                presentFragment(new FormattingMenuActivity());
                break;

            case ID_DISABLE_QUICK_REACTION:
                togglePref("disableQuickReaction", item, view);
                break;
            case ID_HIDE_MESSAGE_REACTIONS:
                togglePref("hideMessageReactions", item, view);
                break;
            case ID_HIDE_SAVED_MESSAGES_TAGS:
                togglePref("hideSavedMessagesTags", item, view);
                break;
            case ID_DISABLE_LOCKED_ANIMATED_EMOJI:
                togglePref("disableLockedAnimatedEmoji", item, view);
                break;

            case ID_FULL_RECENT_STICKERS:
                togglePref("fullRecentStickers", item, view);
                break;
            case ID_CONFIRM_GIF_STICKER_SEND:
                togglePref("confirmGifStickerSend", item, view);
                break;
            case ID_SHOW_ARCHIVED_STICKERS:
                if (togglePref("showArchivedStickers", item, view)) {
                    MediaDataController.getInstance(currentAccount).loadArchivedStickerSets();
                }
                break;

            case ID_INAPP_CAMERA:
                SharedConfig.toggleInappCamera();
                item.checked = SharedConfig.inappCamera;
                setCellChecked(view, SharedConfig.inappCamera);
                updateList(true);
                break;
            case ID_SYSTEM_CAMERA:
                if (SharedConfig.inappCamera) {
                    togglePref("systemCamera", item, view);
                }
                break;
            case ID_PHOTO_HAS_STICKER:
                togglePref("photoHasSticker", item, view);
                break;
            case ID_DISABLE_FLIP_PHOTOS:
                togglePref("disableFlipPhotos", item, view);
                break;
            case ID_REAR_VIDEO_MESSAGES:
                togglePref("rearVideoMessages", item, view);
                break;

            case ID_VOICE_QUALITY:
                showVoiceQualityDialog();
                break;
            case ID_DISABLE_AUTOPLAY_NEXT_VOICE:
                togglePref("disableAutoplayNextVoice", item, view);
                break;
            case ID_VOICE_FILE_SIZE:
                togglePref(VoiceFileSize.PREF_ENABLED, item, view);
                break;

            case ID_BOT_SKIP_SHARE:
                togglePref("botSkipShare", item, view);
                break;
            case ID_BOT_SKIP_FULLSCREEN:
                togglePref("botSkipFullscreen", item, view);
                break;
            case ID_QUICK_REPEAT:
                togglePref(QuickRepeat.PREF_KEY, item, view);
                QuickRepeat.invalidate();
                break;

            case ID_WEBSOCKET_TRANSPORT: {
                boolean enabled = togglePref("webSocketTransport", item, view);
                ConnectionsManager.setWebSocketEnabled(enabled, prefs().getString("webSocketDomain", ""));
                updateList(true);
                break;
            }
            case ID_WEBSOCKET_DOMAIN:
                showWebSocketDomainDialog();
                break;

            case ID_DISABLE_TABLET_MODE:
                SharedConfig.toggleForceDisableTabletMode();
                item.checked = SharedConfig.forceDisableTabletMode;
                setCellChecked(view, SharedConfig.forceDisableTabletMode);
                restartApplication();
                break;
            case ID_LOCK_PREMIUM: {
                boolean locked = togglePref("lockPremium", item, view);
                MessagesController.getInstance(currentAccount).premiumLocked = locked;
                break;
            }
            case ID_RESET_LAUNCHER_SHORTCUTS:
                showResetLauncherShortcutsDialog();
                break;

            case ID_EXPORT_SETTINGS:
                exportSettings();
                break;
            case ID_IMPORT_SETTINGS:
                importSettings();
                break;
        }
    }

    private void restartApplication() {
        Activity activity = getParentActivity();
        if (activity != null) {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(activity.getPackageName());
            if (intent != null) {
                activity.finishAffinity();
                activity.startActivity(intent);
            }
        }
        System.exit(0);
    }

    private static String getWebSocketDomainText() {
        String domain = prefs().getString("webSocketDomain", "");
        return TextUtils.isEmpty(domain) ? LocaleController.getString(R.string.WebSocketDomainAuto) : domain;
    }

    private void applyWebSocketDomain(String raw) {
        String trimmed = raw != null ? raw.trim() : "";
        String domain = ConnectionsManager.normalizeWebSocketDomain(trimmed);
        if (domain.isEmpty() && !trimmed.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InvalidFormatError)).show();
            return;
        }
        prefs().edit().putString("webSocketDomain", domain).apply();
        updateList(true);
        if (pref("webSocketTransport", false)) {
            ConnectionsManager.setWebSocketEnabled(true, domain);
        }
    }

    private void showWebSocketDomainDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.WebSocketDomain));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(LocaleController.getString(R.string.WebSocketDomainAuto));
        editText.setBackground(Theme.createEditTextDrawable(context, true));
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        String current = prefs().getString("webSocketDomain", "");
        editText.setText(current);
        editText.setSelection(current.length());
        textLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));

        builder.setView(textLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> applyWebSocketDomain(editText.getText().toString()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                alertDialog.dismiss();
                applyWebSocketDomain(editText.getText().toString());
                return true;
            }
            return false;
        });
        showDialog(alertDialog);
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void showRadioDialog(CharSequence title, String[] options, int selectedIndex, Utilities.Callback<Integer> onSelected) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);

        for (int i = 0; i < options.length; i++) {
            RadioColorCell cell = new RadioColorCell(activity);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setTag(i);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options[i], selectedIndex == i);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            linearLayout.addView(cell);

            cell.setOnClickListener(v -> {
                onSelected.run((Integer) v.getTag());
                builder.getDismissRunnable().run();
            });
        }

        builder.setView(linearLayout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private String getDefaultFolderText() {
        int id = prefs().getInt("defaultFolderId", -1);
        if (id != -1) {
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
            for (int a = 0; a < filters.size(); a++) {
                MessagesController.DialogFilter filter = filters.get(a);
                if (!filter.isDefault() && filter.localId == id) {
                    return filter.name;
                }
            }
        }
        return LocaleController.getString(R.string.FilterAllChats);
    }

    private void showDefaultFolderDialog() {
        ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        for (int a = 0; a < filters.size(); a++) {
            MessagesController.DialogFilter filter = filters.get(a);
            if (filter.isDefault()) {
                names.add(LocaleController.getString(R.string.FilterAllChats));
                ids.add(-1);
            } else {
                names.add(filter.name);
                ids.add(filter.localId);
            }
        }
        if (names.isEmpty()) {
            names.add(LocaleController.getString(R.string.FilterAllChats));
            ids.add(-1);
        }
        int currentId = prefs().getInt("defaultFolderId", -1);
        int selectedIndex = 0;
        final int[] idsArr = new int[ids.size()];
        for (int a = 0; a < ids.size(); a++) {
            idsArr[a] = ids.get(a);
            if (idsArr[a] == currentId) {
                selectedIndex = a;
            }
        }
        showRadioDialog(LocaleController.getString(R.string.DefaultFolder), names.toArray(new String[0]), selectedIndex, index -> {
            prefs().edit().putInt("defaultFolderId", idsArr[index]).apply();
            updateList(false);
        });
    }

    private static String getVoiceQualityText() {
        int bitrate = prefs().getInt("voiceQualityBitrate", -1);
        if (bitrate <= 0) return LocaleController.getString(R.string.VoiceQualityMax);
        if (bitrate <= 16000) return LocaleController.getString(R.string.VoiceQualityLow);
        if (bitrate <= 32000) return LocaleController.getString(R.string.VoiceQualityMedium);
        if (bitrate <= 64000) return LocaleController.getString(R.string.VoiceQualityHigh);
        return LocaleController.getString(R.string.VoiceQualityMax);
    }

    private void showVoiceQualityDialog() {
        String[] options = {
                LocaleController.getString(R.string.VoiceQualityLow),
                LocaleController.getString(R.string.VoiceQualityMedium),
                LocaleController.getString(R.string.VoiceQualityHigh),
                LocaleController.getString(R.string.VoiceQualityMax)
        };
        final int[] bitrates = {16000, 32000, 64000, -1};

        int currentBitrate = prefs().getInt("voiceQualityBitrate", -1);
        int selectedIndex = bitrates.length - 1;
        for (int i = 0; i < bitrates.length; i++) {
            if (bitrates[i] == currentBitrate) {
                selectedIndex = i;
                break;
            }
        }

        showRadioDialog(LocaleController.getString(R.string.VoiceMessageQuality), options, selectedIndex, index -> {
            prefs().edit().putInt("voiceQualityBitrate", bitrates[index]).apply();
            updateList(false);
        });
    }

    private void showResetLauncherShortcutsDialog() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.ResetLauncherShortcuts));
        builder.setMessage(LocaleController.getString(R.string.ResetLauncherShortcutsConfirm));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Reset), (dialog, which) ->
                Utilities.globalQueue.postRunnable(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ShortcutManagerCompat.removeAllDynamicShortcuts(ApplicationLoader.applicationContext);
                        MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts();
                    }
                }));
        showDialog(builder.create());
    }

    private void exportSettings() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "balalgram_settings.json");
            startActivityForResult(intent, REQUEST_EXPORT_SETTINGS);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void importSettings() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_IMPORT_SETTINGS);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showSettingsBackupInfo(String message) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.BalalSettingsTitle));
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        Context context = ApplicationLoader.applicationContext;
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(SettingsBackup.export(context).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                showSettingsBackupInfo(LocaleController.getString(R.string.ExportSettingsDone));
            } catch (Exception e) {
                FileLog.e(e);
                showSettingsBackupInfo(LocaleController.getString(R.string.ImportSettingsError));
            }
        } else if (requestCode == REQUEST_IMPORT_SETTINGS) {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while (in != null && (read = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                boolean ok = SettingsBackup.restore(context, new String(baos.toByteArray(), StandardCharsets.UTF_8));
                showSettingsBackupInfo(LocaleController.getString(ok ? R.string.ImportSettingsRestart : R.string.ImportSettingsError));
            } catch (Exception e) {
                FileLog.e(e);
                showSettingsBackupInfo(LocaleController.getString(R.string.ImportSettingsError));
            }
        }
    }
}
