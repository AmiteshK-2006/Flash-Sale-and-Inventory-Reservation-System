import os

base_pkg = "/Users/amitesh/Documents/antigravity/optimistic-pascal/flash-sale-system/backend/src/main/java/com/flashsale"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

java_files = {
    "simulation/SimulatorService.java": """package com.flashsale.simulation;

import com.flashsale.reservation.ReservationService;
import com.flashsale.payment.PaymentService;
import com.flashsale.auth.UserRepository;
import com.flashsale.auth.User;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SimulatorService {
    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final SimulationRunRepository simulationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SimulatorService(ReservationService reservationService, PaymentService paymentService,
                            SimulationRunRepository simulationRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.simulationRepository = simulationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public SimulationRun runSimulation(Long saleId, int users, int attempts, int successRate) throws Exception {
        SimulationRun run = new SimulationRun();
        run.setId(UUID.randomUUID().toString());
        run.setStatus("RUNNING");
        run.setCreatedAt(OffsetDateTime.now());
        run.setConfiguration(objectMapper.writeValueAsString(java.util.Map.of("users", users, "attempts", attempts)));
        simulationRepository.save(run);

        // Pre-create a dummy user for simulation
        User simUser = userRepository.findByEmail("sim@flashsale.local").orElseGet(() -> {
            User u = new User();
            u.setName("Sim User");
            u.setEmail("sim@flashsale.local");
            u.setPasswordHash("dummy");
            u.setRole("USER");
            u.setCreatedAt(OffsetDateTime.now());
            return userRepository.save(u);
        });

        new Thread(() -> {
            try {
                // Use virtual threads if available, else thread pool
                ExecutorService executor = Executors.newFixedThreadPool(Math.min(attempts, 200));
                
                AtomicInteger successfulRes = new AtomicInteger(0);
                AtomicInteger rejectedReq = new AtomicInteger(0);
                AtomicInteger failedReq = new AtomicInteger(0);
                
                long start = System.currentTimeMillis();
                for (int i = 0; i < attempts; i++) {
                    executor.submit(() -> {
                        try {
                            String idem = UUID.randomUUID().toString();
                            var res = reservationService.reserve(simUser.getId(), saleId, 1, idem);
                            successfulRes.incrementAndGet();
                            
                            // Try payment
                            try {
                                paymentService.processPayment(simUser.getId(), res.getId(), UUID.randomUUID().toString());
                            } catch (Exception pe) {
                                // Payment fail
                            }
                        } catch (Exception e) {
                            if (e.getMessage() != null && (e.getMessage().contains("INSUFFICIENT_STOCK") || e.getMessage().contains("SALE_NOT_ACTIVE"))) {
                                rejectedReq.incrementAndGet();
                            } else {
                                failedReq.incrementAndGet();
                            }
                        }
                    });
                }
                
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.MINUTES);
                
                run.setSuccessfulReservations(successfulRes.get());
                run.setRejectedRequests(rejectedReq.get());
                run.setFailedRequests(failedReq.get());
                run.setTotalRequests(attempts);
                run.setDurationMs(System.currentTimeMillis() - start);
                run.setThroughput((attempts * 1000.0) / run.getDurationMs());
                run.setStatus("COMPLETED");
                run.setCompletedAt(OffsetDateTime.now());
                simulationRepository.save(run);
            } catch (Exception e) {
                run.setStatus("FAILED");
                simulationRepository.save(run);
            }
        }).start();

        return run;
    }
}
""",
    "simulation/SimulationController.java": """package com.flashsale.simulation;

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
"""
}

for path, content in java_files.items():
    write_file(f"{base_pkg}/{path}", content)
