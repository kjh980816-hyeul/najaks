package com.najacks.backend.domain.stream.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChzzkLiveDetail {
    private String channelId;
    private String status;        // "OPEN" | "CLOSE"
    private String liveId;
    private String liveTitle;
    private String liveCategoryValue;
    private Integer concurrentUserCount;
    private String openDate;

    public boolean isLive() {
        return "OPEN".equalsIgnoreCase(status);
    }
}
