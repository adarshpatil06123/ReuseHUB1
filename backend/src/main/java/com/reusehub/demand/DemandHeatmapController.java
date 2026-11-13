package com.reusehub.demand;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demand")
public class DemandHeatmapController {
  private final DemandHeatmapService demandHeatmapService;

  public DemandHeatmapController(DemandHeatmapService demandHeatmapService) {
    this.demandHeatmapService = demandHeatmapService;
  }

  @GetMapping("/heatmap")
  public List<DemandHeatmapZoneResponse> getHeatmap() {
    return demandHeatmapService.getZones();
  }

  @GetMapping("/requests")
  public List<PublicNeedRequestResponse> getPublicNeedRequests() {
    return demandHeatmapService.getPublicNeedRequests();
  }
}
