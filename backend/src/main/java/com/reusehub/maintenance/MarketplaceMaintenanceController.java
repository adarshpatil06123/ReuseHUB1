package com.reusehub.maintenance;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/maintenance/marketplace")
public class MarketplaceMaintenanceController {
  private final MarketplaceMaintenanceService maintenanceService;

  public MarketplaceMaintenanceController(MarketplaceMaintenanceService maintenanceService) {
    this.maintenanceService = maintenanceService;
  }

  @DeleteMapping("/listings")
  public ResponseEntity<Map<String, Long>> purgeListings() {
    return ResponseEntity.ok(maintenanceService.purgeMarketplaceListings());
  }
}