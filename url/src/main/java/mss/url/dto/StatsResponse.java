package mss.url.dto;

import java.util.List;

public record StatsResponse(String code, long totalClicks, long uniqueVisitors, List<DailyClicks> daily) {

}
