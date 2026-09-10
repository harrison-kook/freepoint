package kr.co.freepoint.adapter.web;

public record PointPolicyRequest(long maxEarnAmount,
                                 long maxBalanceAmount,
                                 int minExpireDays,
                                 int maxExpireDays,
                                 int defaultExpireDays) {
}
