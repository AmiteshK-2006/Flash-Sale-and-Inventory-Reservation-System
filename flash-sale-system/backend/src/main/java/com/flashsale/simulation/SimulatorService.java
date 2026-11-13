package com.flashsale.simulation;

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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
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
        return runSimulation(saleId, users, attempts, successRate, "REDIS_LUA");
    }

    public SimulationRun runSimulation(Long saleId, int users, int attempts, int successRate, String engine) throws Exception {
        final String chosenEngine = (engine != null && engine.equalsIgnoreCase("DIRECT_DB")) ? "DIRECT_DB" : "REDIS_LUA";
        SimulationRun run = new SimulationRun();
        run.setId(UUID.randomUUID().toString());
        run.setStatus("RUNNING");
        run.setCreatedAt(OffsetDateTime.now());
        
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("users", users);
        configMap.put("attempts", attempts);
        configMap.put("engine", chosenEngine);
        configMap.put("paymentSuccessRate", successRate);
        run.setConfiguration(objectMapper.writeValueAsString(configMap));
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

        // Ensure drop metadata and stock is warm in Redis before simulation burst
        if (!chosenEngine.equals("DIRECT_DB")) {
            reservationService.hydrateRedis(saleId);
        }

        new Thread(() -> {
            try {
                ExecutorService executor = Executors.newFixedThreadPool(Math.min(attempts, 200));
                
                AtomicInteger successfulRes = new AtomicInteger(0);
                AtomicInteger rejectedReq = new AtomicInteger(0);
                AtomicInteger failedReq = new AtomicInteger(0);
                ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
                
                long start = System.currentTimeMillis();
                for (int i = 0; i < attempts; i++) {
                    executor.submit(() -> {
                        long reqStart = System.nanoTime();
                        try {
                            String idem = UUID.randomUUID().toString();
                            var res = chosenEngine.equals("DIRECT_DB") 
                                    ? reservationService.reserveDirectDb(simUser.getId(), saleId, 1, idem)
                                    : reservationService.reserve(simUser.getId(), saleId, 1, idem);
                            successfulRes.incrementAndGet();
                            
                            // Try payment
                            try {
                                paymentService.processPayment(simUser.getId(), res.getId(), UUID.randomUUID().toString(), successRate / 100.0);
                            } catch (Exception pe) {
                                // Payment fail
                            }
                        } catch (Exception e) {
                            if (e.getMessage() != null && (e.getMessage().contains("INSUFFICIENT_STOCK") || 
                                                           e.getMessage().contains("SALE_NOT_ACTIVE") ||
                                                           e.getMessage().contains("SALE_OUT_OF_WINDOW") ||
                                                           e.getMessage().contains("STOCK_NOT_INITIALIZED"))) {
                                rejectedReq.incrementAndGet();
                            } else {
                                failedReq.incrementAndGet();
                                if (failedReq.get() == 1) e.printStackTrace();
                            }
                        } finally {
                            long latMs = (System.nanoTime() - reqStart) / 1_000_000;
                            latencies.add(latMs);
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
                long dur = Math.max(1, run.getDurationMs());
                run.setThroughput(java.math.BigDecimal.valueOf((attempts * 1000.0) / dur).setScale(2, java.math.RoundingMode.HALF_UP));

                // Compute latency stats
                List<Long> latList = new ArrayList<>(latencies);
                Collections.sort(latList);
                if (!latList.isEmpty()) {
                    long min = latList.get(0);
                    long max = latList.get(latList.size() - 1);
                    double sum = 0;
                    for (Long l : latList) sum += l;
                    double avg = sum / latList.size();
                    long p50 = latList.get((int) (latList.size() * 0.50));
                    long p95 = latList.get((int) (latList.size() * 0.95));
                    long p99 = latList.get(Math.min(latList.size() - 1, (int) (latList.size() * 0.99)));

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("engine", chosenEngine);
                    stats.put("minMs", min);
                    stats.put("maxMs", max);
                    stats.put("avgMs", Math.round(avg * 100.0) / 100.0);
                    stats.put("p50Ms", p50);
                    stats.put("p95Ms", p95);
                    stats.put("p99Ms", p99);
                    run.setLatencyStatistics(objectMapper.writeValueAsString(stats));
                }

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
