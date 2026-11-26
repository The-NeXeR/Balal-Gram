package org.telegram.messenger.balalgram;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Lightweight TL-RPC helper methods for BalalGram extensions.
 */
public final class BalalApi {

    public interface MessagesCallback {
        void onResult(ArrayList<TLRPC.Message> messages, TLRPC.TL_error error);
    }

    private BalalApi() {
    }

    /**
     * Fetches full server message objects for resolving missing forward dates or expired file references.
     */
    public static void tlrpcMessages(int currentAccount, ArrayList<MessageObject> messages, MessagesCallback callback) {
        if (messages == null || messages.isEmpty() || callback == null) {
            return;
        }
        final AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        final long channelId = messages.get(0).getChannelId();
        final ArrayList<Integer> ids = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ids.add(messages.get(i).getRealId());
        }
        final TLObject req;
        if (channelId != 0) {
            TLRPC.TL_channels_getMessages r = new TLRPC.TL_channels_getMessages();
            r.channel = accountInstance.getMessagesController().getInputChannel(channelId);
            r.id = ids;
            req = r;
        } else {
            TLRPC.TL_messages_getMessages r = new TLRPC.TL_messages_getMessages();
            r.id = ids;
            req = r;
        }
        accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
            TLRPC.messages_Messages msgRes = response instanceof TLRPC.messages_Messages
                    ? (TLRPC.messages_Messages) response
                    : new TLRPC.TL_messages_messagesNotModified();
            callback.onResult(msgRes.messages, error);
        });
    }
}
