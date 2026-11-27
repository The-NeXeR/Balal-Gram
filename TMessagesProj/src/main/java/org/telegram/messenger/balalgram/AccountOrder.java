package org.telegram.messenger.balalgram;

import android.text.TextUtils;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores and applies custom ordering for logged-in Telegram accounts.
 */
public final class AccountOrder {

    private static final String KEY_ORDER = "balal_accountsOrder";
    private static final String SEPARATOR = ",";

    private AccountOrder() {
    }

    public static void collectAccountNumbers(ArrayList<Integer> out) {
        collectAccountNumbers(out, -1, null);
    }

    public static void collectAccountNumbers(ArrayList<Integer> out, int excludedAccount) {
        collectAccountNumbers(out, excludedAccount, null);
    }

    public static void collectAccountNumbers(ArrayList<Integer> out, int excludedAccount, Boolean testBackend) {
        out.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a == excludedAccount) {
                continue;
            }
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            if (testBackend != null && ConnectionsManager.getInstance(a).isTestBackend() != testBackend) {
                continue;
            }
            out.add(a);
        }
        sort(out);
    }

    public static void sort(ArrayList<Integer> accounts) {
        final ArrayList<Long> order = load();
        if (order.isEmpty()) {
            Collections.sort(accounts, AccountOrder::compareByLoginTime);
            return;
        }
        Collections.sort(accounts, (account1, account2) -> {
            int index1 = order.indexOf(UserConfig.getInstance(account1).getClientUserId());
            int index2 = order.indexOf(UserConfig.getInstance(account2).getClientUserId());
            if (index1 >= 0 && index2 >= 0) {
                return Integer.compare(index1, index2);
            } else if (index1 == index2) {
                return compareByLoginTime(account1, account2);
            } else if (index1 < 0) {
                return 1;
            } else {
                return -1;
            }
        });
    }

    public static void save(List<Integer> reordered) {
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        sort(accounts);
        int index = 0;
        for (int i = 0; i < accounts.size() && index < reordered.size(); i++) {
            if (reordered.contains(accounts.get(i))) {
                accounts.set(i, reordered.get(index++));
            }
        }
        ArrayList<Long> ids = new ArrayList<>(accounts.size());
        for (int account : accounts) {
            ids.add(UserConfig.getInstance(account).getClientUserId());
        }
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_ORDER, TextUtils.join(SEPARATOR, ids))
                .apply();
    }

    private static int compareByLoginTime(int account1, int account2) {
        return Integer.compare(
                UserConfig.getInstance(account1).loginTime,
                UserConfig.getInstance(account2).loginTime
        );
    }

    private static ArrayList<Long> load() {
        ArrayList<Long> ids = new ArrayList<>();
        String value = MessagesController.getGlobalMainSettings().getString(KEY_ORDER, null);
        if (TextUtils.isEmpty(value)) {
            return ids;
        }
        String[] parts = value.split(SEPARATOR);
        for (String part : parts) {
            try {
                long id = Long.parseLong(part);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
