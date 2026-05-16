package com.najacks.backend.domain.stream.event;

import com.najacks.backend.domain.stream.external.ChzzkLiveDetail;

public record StreamStartedEvent(Long streamerNo, ChzzkLiveDetail detail) {}
