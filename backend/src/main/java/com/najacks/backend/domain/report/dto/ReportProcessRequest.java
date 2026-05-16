package com.najacks.backend.domain.report.dto;

import com.najacks.backend.domain.report.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record ReportProcessRequest(
        @NotNull(message = "처리 결과는 필수입니다")
        ReportStatus action
) {
}
