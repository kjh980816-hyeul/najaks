package com.najacks.backend.domain.report.weekly.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeeklyInsight {
    private String headline;
    private List<String> highlights;
    private List<String> concerns;
    private String recommendation;
}
