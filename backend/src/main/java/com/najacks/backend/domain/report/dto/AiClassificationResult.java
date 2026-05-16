package com.najacks.backend.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiClassificationResult {

    /** ABUSE | SPAM | SEXUAL | FALSE_INFO | COPYRIGHT | ETC */
    private String category;

    /** 1~5 */
    private Integer severity;

    /** 200자 이내 요약 */
    private String summary;

    /** 최대 5개 */
    private List<String> keywords;
}
