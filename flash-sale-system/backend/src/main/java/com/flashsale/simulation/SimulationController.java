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
    public ResponseEntity<?> startSimulation(@RequestBody Map<String, Object> req) {
        try {
            Long saleId = Long.valueOf(req.get("saleId").toString());
            int shoppers = req.containsKey("shoppers") 
                    ? Integer.parseInt(req.get("shoppers").toString())
                    : Integer.parseInt(req.getOrDefault("attempts", "1000").toString());
            int users = req.containsKey("users") 
                    ? Integer.parseInt(req.get("users").toString()) 
                    : shoppers;
            int attempts = req.containsKey("attempts") 
                    ? Integer.parseInt(req.get("attempts").toString()) 
                    : shoppers;
            int successRate = req.containsKey("paymentSuccessRate") 
                    ? Integer.parseInt(req.get("paymentSuccessRate").toString()) 
                    : 100;
            String engine = req.containsKey("engine") ? req.get("engine").toString() : "REDIS_LUA";

            SimulationRun run = simulatorService.runSimulation(saleId, users, attempts, successRate, engine);
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
