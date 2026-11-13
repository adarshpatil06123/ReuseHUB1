package com.reusehub.demand;

public record DemandHeatmapZoneResponse(
    String location,
    long wishlistSaves,
    long openRequests,
    long needRequests,
    long demandScore
) {}
