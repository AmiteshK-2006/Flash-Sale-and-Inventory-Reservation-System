package com.flashsale.simulation;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/simulations")
public class SimulationController {
    private final SimulatorService simulatorService;
    private final SimulationRunRepository simulationRepository;

    public SimulationController(SimulatorService simulatorService, SimulationRunRepository simulationRepository) {
        this.simulatorService = simulatorService;
        this.simulationRepository = simulationRepository;
    }

    @PostMapping
    public ResponseEntity<?> startSimulation(@RequestBody Map<String, Integer> req) {
        try {
            Long saleId = req.get("saleId").longValue();
            int users = req.getOrDefault("users", 1000);
            int attempts = req.getOrDefault("attempts", 1000);
            int successRate = req.getOrDefault("paymentSuccessRate", 90);
            
            SimulationRun run = simulatorService.runSimulation(saleId, users, attempts, successRate);
            return ResponseEntity.ok(run);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSimulation(@PathVariable String id) {
        return simulationRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
