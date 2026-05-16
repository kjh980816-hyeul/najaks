package com.najacks.backend.domain.chat.chzzk;

import io.socket.client.IO;
import io.socket.client.Socket;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.net.URI;
import java.util.function.Consumer;

/**
 * 치지직 채팅 세션 Socket.IO 래퍼.
 * - 세션 URL(wss://ssio...) 로 연결
 * - SYSTEM connected → sessionKey 받으면 onReady 콜백 호출 (여기서 subscribeChat API 호출 유도)
 * - CHAT 이벤트 → 메시지 파싱해서 onMessage 콜백으로 전달
 */
@Slf4j
public class ChzzkChatSocket {

    private final String sessionUrl;
    private final Consumer<String> onReady;       // sessionKey 전달
    private final Consumer<ChzzkChatMessage> onMessage;
    private final Runnable onClose;

    private volatile Socket socket;
    private volatile String sessionKey;

    public ChzzkChatSocket(String sessionUrl,
                           Consumer<String> onReady,
                           Consumer<ChzzkChatMessage> onMessage,
                           Runnable onClose) {
        this.sessionUrl = sessionUrl;
        this.onReady = onReady;
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    public void connect() throws Exception {
        IO.Options opts = new IO.Options();
        opts.reconnection = false;
        opts.forceNew = true;
        opts.timeout = 5000;
        opts.transports = new String[]{"websocket"};

        Socket s = IO.socket(URI.create(sessionUrl), opts);

        s.on(Socket.EVENT_CONNECT, args -> log.info("치지직 세션 연결됨 url={}", maskUrl(sessionUrl)));
        s.on(Socket.EVENT_DISCONNECT, args -> {
            log.info("치지직 세션 연결 해제");
            if (onClose != null) onClose.run();
        });
        s.on(Socket.EVENT_CONNECT_ERROR, args -> log.warn("치지직 세션 연결 에러 args={}", safeToString(args)));

        s.on("SYSTEM", args -> handleSystem(args));
        s.on("CHAT", args -> handleChat(args));

        s.connect();
        this.socket = s;
    }

    public void close() {
        try {
            if (socket != null) {
                socket.off();
                socket.disconnect();
                socket.close();
            }
        } catch (Exception e) {
            log.debug("socket close exception (ignored): {}", e.getMessage());
        }
    }

    public String getSessionKey() { return sessionKey; }

    private void handleSystem(Object[] args) {
        try {
            JSONObject root = firstJson(args);
            if (root == null) return;
            String type = root.optString("type", "");
            JSONObject data = root.optJSONObject("data");
            if ("connected".equalsIgnoreCase(type) && data != null) {
                this.sessionKey = data.optString("sessionKey", null);
                log.info("치지직 세션 connected sessionKey={}", mask(sessionKey));
                if (onReady != null && sessionKey != null) onReady.accept(sessionKey);
            } else if ("subscribed".equalsIgnoreCase(type)) {
                log.info("치지직 이벤트 구독됨: {}", data);
            } else if ("revoked".equalsIgnoreCase(type)) {
                log.warn("치지직 구독 권한 취소됨: {}", data);
            }
        } catch (Exception e) {
            log.warn("SYSTEM 이벤트 처리 실패", e);
        }
    }

    private void handleChat(Object[] args) {
        try {
            JSONObject root = firstJson(args);
            if (root == null) return;
            JSONObject profile = root.optJSONObject("profile");
            ChzzkChatMessage msg = ChzzkChatMessage.builder()
                    .channelId(root.optString("channelId", null))
                    .senderChannelId(root.optString("senderChannelId", null))
                    .nickname(profile != null ? profile.optString("nickname", null) : null)
                    .userRoleCode(root.optString("userRoleCode", null))
                    .content(root.optString("content", ""))
                    .messageTime(root.optLong("messageTime", System.currentTimeMillis()))
                    .build();
            if (onMessage != null) onMessage.accept(msg);
        } catch (Exception e) {
            log.warn("CHAT 이벤트 처리 실패", e);
        }
    }

    private JSONObject firstJson(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object a = args[0];
        if (a instanceof JSONObject jo) return jo;
        if (a instanceof String s) {
            try { return new JSONObject(s); } catch (Exception ignored) {}
        }
        return null;
    }

    private String safeToString(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i] != null ? args[i].toString() : "null");
        }
        sb.append("]");
        return sb.toString();
    }

    private String mask(String s) {
        if (s == null) return null;
        if (s.length() <= 8) return "***";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4);
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) + "?auth=***" : url;
    }
}
