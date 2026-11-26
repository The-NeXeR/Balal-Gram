package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Searchable settings screen for BalalGram client customizations and connection options.
 */
public class BalalSettingsActivity extends BaseFragment {

    public static final int ID_HIDE_SENSITIVE_DATA = 1;
    public static final int ID_HIDE_SENSITIVE_PHONE = 2;
    public static final int ID_HIDE_SENSITIVE_USERNAME = 3;
    public static final int ID_HIDE_SENSITIVE_BIO = 4;
    public static final int ID_HIDE_SENSITIVE_ID = 5;

    public static final int ID_SYNC_PINS = 10;
    public static final int ID_UNMUTED_ON_TOP = 11;
    public static final int ID_OPEN_ARCHIVE_ON_PULL = 12;
    public static final int ID_ENABLE_LAST_SEEN_DOTS = 13;

    public static final int ID_REPLACE_FORWARD = 20;

    public static final int ID_INAPP_CAMERA = 30;
    public static final int ID_SYSTEM_CAMERA = 31;
    public static final int ID_PHOTO_HAS_STICKER = 32;
    public static final int ID_REAR_VIDEO_MESSAGES = 33;

    public static final int ID_WEBSOCKET_TRANSPORT = 40;
    public static final int ID_WEBSOCKET_DOMAIN = 41;

    private UniversalRecyclerView listView;
    private int highlightItemId = 0;
    private String searchQuery = "";

    /**
     * Requests the recycler view to scroll to and highlight the specified item id when opened.
     */
    public BalalSettingsActivity highlight(int itemId) {
        this.highlightItemId = itemId;
        return this;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.ForkSettingsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(0, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                    }

                    @Override
                    public void onSearchCollapse() {
                        searchQuery = "";
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText.getText() != null ? editText.getText().toString().trim() : "";
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }
                });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        if (isOpen && !backward && highlightItemId != 0 && listView != null) {
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
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private boolean pref(String key, boolean defaultValue) {
        return prefs().getBoolean(key, defaultValue);
    }

    private void togglePref(String key, UItem item, View view) {
        boolean newValue = !item.checked;
        prefs().edit().putBoolean(key, newValue).apply();
        item.checked = newValue;
        setCellChecked(view, newValue);
    }

    private void setCellChecked(View view, boolean checked) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        } else if (view instanceof NotificationsCheckCell) {
            ((NotificationsCheckCell) view).setChecked(checked);
        }
    }

    private boolean matches(String query, String title, String subtext) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String lower = query.toLowerCase(Locale.US);
        if (title != null && title.toLowerCase(Locale.US).contains(lower)) {
            return true;
        }
        return subtext != null && subtext.toLowerCase(Locale.US).contains(lower);
    }

    private void addCheck(ArrayList<UItem> items, String query, int id, int titleRes, boolean checked) {
        String title = LocaleController.getString(titleRes);
        if (matches(query, title, null)) {
            items.add(UItem.asCheck(id, title).setChecked(checked));
        }
    }

    private void addButtonCheck(ArrayList<UItem> items, String query, int id, int titleRes, int infoRes, boolean checked, boolean enabled) {
        String title = LocaleController.getString(titleRes);
        String info = LocaleController.getString(infoRes);
        if (matches(query, title, info)) {
            items.add(UItem.asButtonCheck(id, title, info).setChecked(checked).setEnabled(enabled));
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String query = searchQuery;
        final boolean searching = !TextUtils.isEmpty(query);

        if (!searching) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.PrivacyTitle)));
        }
        addCheck(items, query, ID_HIDE_SENSITIVE_DATA, R.string.HideSensitiveData, SharedConfig.hideSensitiveData());
        if (SharedConfig.hideSensitiveData() || searching) {
            addCheck(items, query, ID_HIDE_SENSITIVE_PHONE, R.string.HideSensitivePhone, pref("hideSensitivePhone", true));
            addCheck(items, query, ID_HIDE_SENSITIVE_USERNAME, R.string.HideSensitiveUsername, pref("hideSensitiveUsername", true));
            addCheck(items, query, ID_HIDE_SENSITIVE_BIO, R.string.HideSensitiveBio, pref("hideSensitiveBio", true));
            addCheck(items, query, ID_HIDE_SENSITIVE_ID, R.string.HideSensitiveId, pref("hideSensitiveId", true));
        }

        if (!searching) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.FilterChats)));
        }
        addButtonCheck(items, query, ID_SYNC_PINS, R.string.SyncPins, R.string.SyncPinsInfo, pref("syncPins", true), true);
        addButtonCheck(items, query, ID_UNMUTED_ON_TOP, R.string.UnmutedOnTop, R.string.UnmutedOnTopInfo, pref("unmutedOnTop", false), true);
        addButtonCheck(items, query, ID_OPEN_ARCHIVE_ON_PULL, R.string.OpenArchiveOnPull, R.string.OpenArchiveOnPullInfo, pref("openArchiveOnPull", true), true);
        addButtonCheck(items, query, ID_ENABLE_LAST_SEEN_DOTS, R.string.EnableLastSeenDots, R.string.EnableLastSeenDotsInfo, pref("enableLastSeenDots", true), true);

        if (!searching) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.SettingsChat)));
        }
        addButtonCheck(items, query, ID_REPLACE_FORWARD, R.string.ReplaceForward, R.string.ReplaceForwardInfo, pref("replaceForward", true), true);

        if (!searching) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.ForkSectionMedia)));
        }
        addButtonCheck(items, query, ID_INAPP_CAMERA, R.string.InAppCamera, R.string.InAppCameraInfo, SharedConfig.inappCamera, true);
        addButtonCheck(items, query, ID_SYSTEM_CAMERA, R.string.SystemCamera, R.string.SystemCameraInfo, pref("systemCamera", false), SharedConfig.inappCamera);
        addButtonCheck(items, query, ID_PHOTO_HAS_STICKER, R.string.PhotoHasSticker, R.string.PhotoHasStickerInfo, pref("photoHasSticker", true), true);
        addButtonCheck(items, query, ID_REAR_VIDEO_MESSAGES, R.string.RearVideoMessages, R.string.RearVideoMessagesInfo, pref("rearVideoMessages", false), true);

        if (!searching) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(LocaleController.getString(R.string.ProxySettings)));
        }
        boolean wsEnabled = pref("webSocketTransport", false);
        addButtonCheck(items, query, ID_WEBSOCKET_TRANSPORT, R.string.WebSocketTransport, R.string.WebSocketTransportInfo, wsEnabled, true);
        if (wsEnabled || searching) {
            String wsTitle = LocaleController.getString(R.string.WebSocketDomain);
            String domain = prefs().getString("webSocketDomain", "");
            String wsValue = TextUtils.isEmpty(domain) ? LocaleController.getString(R.string.WebSocketDomainAuto) : domain;
            if (matches(query, wsTitle, wsValue)) {
                items.add(UItem.asSettingsCell(ID_WEBSOCKET_DOMAIN, wsTitle, wsValue));
            }
        }
        if (!searching) {
            items.add(UItem.asShadow(null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_HIDE_SENSITIVE_DATA:
                togglePref("hideSensitiveData", item, view);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
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
            case ID_ENABLE_LAST_SEEN_DOTS:
                togglePref("enableLastSeenDots", item, view);
                break;
            case ID_REPLACE_FORWARD:
                togglePref("replaceForward", item, view);
                break;
            case ID_INAPP_CAMERA:
                SharedConfig.toggleInappCamera();
                item.checked = SharedConfig.inappCamera;
                setCellChecked(view, SharedConfig.inappCamera);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                break;
            case ID_SYSTEM_CAMERA:
                if (!SharedConfig.inappCamera) {
                    return;
                }
                togglePref("systemCamera", item, view);
                break;
            case ID_PHOTO_HAS_STICKER:
                togglePref("photoHasSticker", item, view);
                break;
            case ID_REAR_VIDEO_MESSAGES:
                togglePref("rearVideoMessages", item, view);
                break;
            case ID_WEBSOCKET_TRANSPORT: {
                boolean enabled = !pref("webSocketTransport", false);
                prefs().edit().putBoolean("webSocketTransport", enabled).apply();
                item.checked = enabled;
                setCellChecked(view, enabled);
                ConnectionsManager.setWebSocketEnabled(enabled, prefs().getString("webSocketDomain", ""));
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                break;
            }
            case ID_WEBSOCKET_DOMAIN:
                showWebSocketDomainDialog();
                break;
        }
    }

    private void applyWebSocketDomain(String raw) {
        String trimmed = raw != null ? raw.trim() : "";
        String domain = ConnectionsManager.normalizeWebSocketDomain(trimmed);
        if (domain.isEmpty() && !trimmed.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InvalidFormatError)).show();
            return;
        }
        prefs().edit().putString("webSocketDomain", domain).apply();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
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
}
