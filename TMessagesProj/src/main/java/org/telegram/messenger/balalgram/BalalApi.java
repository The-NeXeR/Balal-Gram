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

    public interface ChatFullCallback {
        void onResult(TLRPC.TL_messages_chatFull fullChat);
    }

    public interface SearchMessagesCallback {
        void onBatch(ArrayList<TLRPC.Message> messages);
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

    /**
     * Fetches full channel info (used to resolve migrated_from_chat_id for bulk delete).
     */
    public static void fullChannel(int currentAccount, long channelId, ChatFullCallback callback) {
        if (channelId == 0L || callback == null) {
            return;
        }
        final AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        final TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
        req.channel = accountInstance.getMessagesController().getInputChannel(channelId);
        accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_chatFull) {
                callback.onResult((TLRPC.TL_messages_chatFull) response);
            }
        });
    }

    /**
     * Paginates through matching messages in a dialog for bulk delete actions.
     */
    public static void searchAllMessages(
            int currentAccount,
            TLRPC.InputPeer peer,
            TLRPC.InputPeer from,
            SearchMessagesCallback step,
            Runnable finish) {
        performSearchPage(currentAccount, peer, from, 0, step, finish);
    }

    private static void performSearchPage(
            int currentAccount,
            TLRPC.InputPeer peer,
            TLRPC.InputPeer from,
            int offsetId,
            SearchMessagesCallback step,
            Runnable finish) {
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.from_id = from;
        req.limit = 100;
        req.offset_id = offsetId;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.q = "";
        req.flags = 1;

        AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(req, (response, error) -> {
            final TLRPC.messages_Messages msgRes = response instanceof TLRPC.messages_Messages
                    ? (TLRPC.messages_Messages) response
                    : new TLRPC.TL_messages_messagesNotModified();
            if (msgRes.messages == null || msgRes.messages.isEmpty()) {
                if (finish != null) {
                    finish.run();
                }
                return;
            }
            int newOffsetId = msgRes.messages.get(0).id;
            final ArrayList<TLRPC.Message> batch = new ArrayList<>(msgRes.messages.size());
            for (int i = 0; i < msgRes.messages.size(); i++) {
                TLRPC.Message message = msgRes.messages.get(i);
                batch.add(message);
                newOffsetId = Math.min(newOffsetId, message.id);
            }
            if (step != null) {
                step.onBatch(batch);
            }
            performSearchPage(currentAccount, peer, from, newOffsetId, step, finish);
        });
    }
}
