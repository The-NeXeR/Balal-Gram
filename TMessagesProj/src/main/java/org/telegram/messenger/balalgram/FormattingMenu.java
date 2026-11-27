package org.telegram.messenger.balalgram;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Persistence and ordering config for the customizable text-selection formatting toolbar.
 */
public final class FormattingMenu {

    public static final String QUOTE = "quote";
    public static final String SPOILER = "spoiler";
    public static final String BOLD = "bold";
    public static final String ITALIC = "italic";
    public static final String MONO = "mono";
    public static final String STRIKE = "strike";
    public static final String UNDERLINE = "underline";
    public static final String LINK = "link";
    public static final String DATE = "date";
    public static final String REGULAR = "regular";

    public static final List<String> DEFAULT_ORDER = Collections.unmodifiableList(Arrays.asList(
            QUOTE, SPOILER, BOLD, ITALIC, MONO, STRIKE, UNDERLINE, LINK, DATE, REGULAR
    ));

    private static final String ORDER_KEY = "formattingMenuOrder";
    private static final String HIDDEN_KEY = "formattingMenuHidden";
    private static final String SEPARATOR = ",";

    private final List<String> order;
    private final List<String> hidden;

    private FormattingMenu(List<String> order, List<String> hidden) {
        this.order = Collections.unmodifiableList(order);
        this.hidden = Collections.unmodifiableList(hidden);
    }

    public List<String> getOrder() {
        return order;
    }

    public List<String> getHidden() {
        return hidden;
    }

    public boolean isVisible(String key) {
        return !hidden.contains(key);
    }

    public List<String> getVisibleOrder() {
        ArrayList<String> visible = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            if (isVisible(key)) {
                visible.add(key);
            }
        }
        return visible;
    }

    public static FormattingMenu load() {
        android.content.SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        return fromStrings(
                preferences.getString(ORDER_KEY, null),
                preferences.getString(HIDDEN_KEY, null)
        );
    }

    public static FormattingMenu fromStrings(String orderStr, String hiddenStr) {
        return new FormattingMenu(
                parseKeys(orderStr, true),
                parseKeys(hiddenStr, false)
        );
    }

    public static void save(List<String> order, List<String> hidden) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(ORDER_KEY, TextUtils.join(SEPARATOR, order))
                .putString(HIDDEN_KEY, TextUtils.join(SEPARATOR, hidden))
                .apply();
    }

    public static void reset() {
        MessagesController.getGlobalMainSettings().edit()
                .remove(ORDER_KEY)
                .remove(HIDDEN_KEY)
                .apply();
    }

    public static int getMenuId(String key) {
        if (key == null) {
            return 0;
        }
        switch (key) {
            case QUOTE:
                return R.id.menu_quote;
            case SPOILER:
                return R.id.menu_spoiler;
            case BOLD:
                return R.id.menu_bold;
            case ITALIC:
                return R.id.menu_italic;
            case MONO:
                return R.id.menu_mono;
            case STRIKE:
                return R.id.menu_strike;
            case UNDERLINE:
                return R.id.menu_underline;
            case LINK:
                return R.id.menu_link;
            case DATE:
                return R.id.menu_date;
            case REGULAR:
                return R.id.menu_regular;
            default:
                return 0;
        }
    }

    public static CharSequence getTitle(String key) {
        if (key == null) {
            return "";
        }
        switch (key) {
            case QUOTE:
                return LocaleController.getString(R.string.Quote);
            case SPOILER:
                return LocaleController.getString(R.string.Spoiler);
            case BOLD:
                return withSpan(R.string.Bold, new TypefaceSpan(AndroidUtilities.bold()));
            case ITALIC:
                return withSpan(R.string.Italic, new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")));
            case MONO:
                return withSpan(R.string.Mono, new TypefaceSpan(Typeface.MONOSPACE));
            case STRIKE:
                return withSpan(R.string.Strike, styleSpan(TextStyleSpan.FLAG_STYLE_STRIKE));
            case UNDERLINE:
                return withSpan(R.string.Underline, styleSpan(TextStyleSpan.FLAG_STYLE_UNDERLINE));
            case LINK:
                return LocaleController.getString(R.string.CreateLink);
            case DATE:
                return LocaleController.getString(R.string.FormattedDate);
            case REGULAR:
                return LocaleController.getString(R.string.Regular);
            default:
                return "";
        }
    }

    private static List<String> parseKeys(String value, boolean appendMissing) {
        ArrayList<String> keys = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            String[] parts = value.split(SEPARATOR, -1);
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (DEFAULT_ORDER.contains(part) && !keys.contains(part)) {
                    keys.add(part);
                }
            }
        }
        if (appendMissing) {
            for (int i = 0; i < DEFAULT_ORDER.size(); i++) {
                String def = DEFAULT_ORDER.get(i);
                if (!keys.contains(def)) {
                    keys.add(def);
                }
            }
        }
        return keys;
    }

    private static CharSequence withSpan(int resId, Object span) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString(resId));
        stringBuilder.setSpan(span, 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return stringBuilder;
    }

    private static TextStyleSpan styleSpan(int flag) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = run.flags | flag;
        return new TextStyleSpan(run);
    }
}
