package com.bium.domain.activity.dto;

import java.util.List;

// 다음 페이지가 없으면 nextCursor는 null이다. 있으면 다음 요청의 cursor로 그대로 보낸다
public record ActivityLogPageResponse(List<ActivityLogResponse> activities, Long nextCursor) {
}
