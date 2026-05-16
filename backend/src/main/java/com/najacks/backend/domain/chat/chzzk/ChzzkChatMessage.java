package com.najacks.backend.domain.chat.chzzk;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChzzkChatMessage {
    private final String channelId;
    private final String senderChannelId;
    private final String nickname;
    private final String content;
    private final String userRoleCode;   // streamer | common_user | manager
    private final long messageTime;      // epoch ms
}
