package com.najacks.backend.domain.stream.event;

public record StreamEndedEvent(Long streamerNo, String streamId) {}
