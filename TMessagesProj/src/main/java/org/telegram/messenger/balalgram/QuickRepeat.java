package org.telegram.messenger.balalgram;

import org.telegram.messenger.MessagesController;

import java.util.HashMap;

/**
 * Manages global and per-dialog Quick Repeat button enablement for bot chats.
 */
public final class QuickRepeat {

    public static final String PREF_KEY = "quickRepeatButton";
    private static final String DIALOG_PREFIX = "quickRepeatDialog_";

    private static final HashMap<Integer, HashMap<Long, Boolean>> explicit = new HashMap<>();

    private QuickRepeat() {
    }

    public static boolean isDefaultEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_KEY, false);
    }

    public static boolean isEnabled(int account, long dialogId) {
        HashMap<Long, Boolean> map = explicitMap(account);
        synchronized (map) {
            Boolean value = map.get(dialogId);
            if (value != null) {
                return value;
            }
        }
        return MessagesController.getMainSettings(account)
                .getBoolean(DIALOG_PREFIX + dialogId, isDefaultEnabled());
    }

    public static void setEnabled(int account, long dialogId, boolean value) {
        MessagesController.getMainSettings(account)
                .edit()
                .putBoolean(DIALOG_PREFIX + dialogId, value)
                .apply();
        HashMap<Long, Boolean> map = explicitMap(account);
        synchronized (map) {
            map.put(dialogId, value);
        }
    }

    public static boolean toggle(int account, long dialogId) {
        boolean value = !isEnabled(account, dialogId);
        setEnabled(account, dialogId, value);
        return value;
    }

    public static void invalidate() {
        synchronized (explicit) {
            explicit.clear();
        }
    }

    private static HashMap<Long, Boolean> explicitMap(int account) {
        synchronized (explicit) {
            HashMap<Long, Boolean> map = explicit.get(account);
            if (map == null) {
                map = new HashMap<>();
                explicit.put(account, map);
            }
            return map;
        }
    }
}
