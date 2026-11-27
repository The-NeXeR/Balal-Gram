package org.telegram.messenger.balalgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;

/**
 * Formats optional file size labels appended to voice message duration text.
 */
public final class VoiceFileSize {

    public static final String PREF_ENABLED = "voiceShowFileSize";

    private VoiceFileSize() {
    }

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_ENABLED, false);
    }

    public static String label(long size) {
        if (!isEnabled()) {
            return "";
        }
        return ", " + AndroidUtilities.formatFileSize(size);
    }
}
