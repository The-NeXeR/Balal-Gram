package org.telegram.messenger.balalgram;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and sends standalone photo or document media attached to a resolved link preview.
 */
public final class ExtractMediaFromPreview {

    private ExtractMediaFromPreview() {
    }

    public static boolean hasMedia(TLRPC.WebPage webPage) {
        return webPage != null && (webPage.photo instanceof TLRPC.TL_photo || webPage.document instanceof TLRPC.TL_document);
    }

    public static boolean isDocument(TLRPC.WebPage webPage) {
        return webPage != null && webPage.document instanceof TLRPC.TL_document;
    }

    public static boolean isPhotoOnly(TLRPC.WebPage webPage) {
        if (webPage == null || !(webPage.photo instanceof TLRPC.TL_photo) || webPage.document instanceof TLRPC.TL_document) {
            return false;
        }
        return TextUtils.isEmpty(webPage.site_name)
                && TextUtils.isEmpty(webPage.title)
                && TextUtils.isEmpty(webPage.description);
    }

    public static boolean removeLink(Editable editable, TLRPC.WebPage webPage) {
        if (editable == null || editable.length() == 0) {
            return false;
        }
        int[] range = linkRange(editable, webPage);
        if (range == null) {
            return false;
        }
        int start = range[0];
        int end = range[1];
        while (end < editable.length() && (editable.charAt(end) == ' ' || editable.charAt(end) == '\t')) {
            end++;
        }
        if (end == range[1]) {
            while (start > 0 && Character.isWhitespace(editable.charAt(start - 1))) {
                start--;
            }
        }
        editable.delete(start, end);
        if (editable.length() > 0 && TextUtils.getTrimmedLength(editable) == 0) {
            editable.clear();
        }
        return true;
    }

    private static int[] linkRange(CharSequence text, TLRPC.WebPage webPage) {
        if (webPage != null) {
            String[] candidates = new String[]{webPage.url, webPage.display_url};
            for (String candidate : candidates) {
                if (TextUtils.isEmpty(candidate)) {
                    continue;
                }
                int index = TextUtils.indexOf(text, candidate);
                if (index >= 0) {
                    return new int[]{index, index + candidate.length()};
                }
            }
        }
        String target = normalizedUrl(webPage != null ? webPage.url : null);
        if (!target.isEmpty() && text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            URLSpanReplacement[] spans = spanned.getSpans(0, spanned.length(), URLSpanReplacement.class);
            for (URLSpanReplacement span : spans) {
                if (!target.equals(normalizedUrl(span.getURL()))) {
                    continue;
                }
                int start = spanned.getSpanStart(span);
                int end = spanned.getSpanEnd(span);
                if (start >= 0 && end > start) {
                    return new int[]{start, end};
                }
            }
        }
        Pattern webUrl = AndroidUtilities.WEB_URL;
        if (webUrl == null) {
            return null;
        }
        Matcher matcher = webUrl.matcher(text);
        int[] firstUrl = null;
        int count = 0;
        while (matcher.find()) {
            count++;
            if (!target.isEmpty() && target.equals(normalizedUrl(text.subSequence(matcher.start(), matcher.end())))) {
                return new int[]{matcher.start(), matcher.end()};
            }
            if (count == 1) {
                firstUrl = new int[]{matcher.start(), matcher.end()};
            }
        }
        return count == 1 ? firstUrl : null;
    }

    public static String normalizedUrl(CharSequence url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String s = url.toString().trim().toLowerCase(Locale.US);
        if (s.startsWith("https://")) {
            s = s.substring("https://".length());
        } else if (s.startsWith("http://")) {
            s = s.substring("http://".length());
        }
        if (s.startsWith("www.")) {
            s = s.substring("www.".length());
        }
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }

    public static boolean send(
            int currentAccount,
            long peer,
            TLRPC.WebPage webPage,
            String caption,
            ArrayList<TLRPC.MessageEntity> entities,
            MessageObject replyToMsg,
            MessageObject replyToTopMsg,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            long payStars
    ) {
        SendMessagesHelper.SendMessageParams params;
        if (webPage.document instanceof TLRPC.TL_document) {
            params = SendMessagesHelper.SendMessageParams.of(
                    (TLRPC.TL_document) webPage.document, null, null, peer,
                    replyToMsg, replyToTopMsg, caption, entities, null, null,
                    notify, scheduleDate, scheduleRepeatPeriod, 0, webPage, null, false
            );
        } else if (webPage.photo instanceof TLRPC.TL_photo) {
            params = SendMessagesHelper.SendMessageParams.of(
                    (TLRPC.TL_photo) webPage.photo, null, peer,
                    replyToMsg, replyToTopMsg, caption, entities, null, null,
                    notify, scheduleDate, scheduleRepeatPeriod, 0, webPage, false
            );
        } else {
            return false;
        }
        params.payStars = payStars;
        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
        return true;
    }
}
