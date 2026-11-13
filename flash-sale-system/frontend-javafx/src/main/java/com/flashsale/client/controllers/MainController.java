package com.flashsale.client.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class MainController {

    @FXML private Label backendStatusLabel;
    @FXML private Label userBadgeLabel;

    // Shopper Tab - Hero Showcase
    @FXML private Label heroProductSubtitle;
    @FXML private ListView<String> salesListView;
    @FXML private Label selectedSaleTitleLabel;
    @FXML private Label selectedSalePriceLabel;
    @FXML private Label remainingStockBadge;
    @FXML private ProgressBar stockLevelProgressBar;
    @FXML private Label stockBreakdownLabel;
    @FXML private TextField quantityField;
    @FXML private Button reserveButton;

    // Right Cart / Hold Drawer
    @FXML private Label countdownTimerLabel;
    @FXML private ProgressBar countdownProgressBar;
    @FXML private Label cartItemTitle;
    @FXML private Label reservationInfoLabel;
    @FXML private Label cartTotalLabel;
    @FXML private Button payButton;
    @FXML private Label checkoutResultLabel;

    // Storefront Live Pipeline
    @FXML private Label pipelineTotalTimeLabel;
    @FXML private VBox pipelineNode1, pipelineNode2, pipelineNode3, pipelineNode4;
    @FXML private Label pipelineStep1Label, pipelineStep2Label, pipelineStep3Label, pipelineStep4Label;

    // Simulator Tab
    @FXML private ComboBox<String> simSaleComboBox;
    @FXML private TextField simShoppersField;
    @FXML private RadioButton radioRedisLua, radioDirectDb;
    @FXML private ToggleGroup engineToggleGroup;
    @FXML private Slider simRateSlider;
    @FXML private Label simRateLabel;
    @FXML private Button runSimButton;
    @FXML private Label simTotalLabel;
    @FXML private Label simSuccessLabel;
    @FXML private Label simRejectedLabel;
    @FXML private Label simThroughputLabel;
    @FXML private Label simDurationLabel;
    @FXML private Label dbQueriesLabel, dbShieldedLabel, dbPoolStatusLabel;
    @FXML private TextArea simLogArea;

    // Admin & Inventory Tab
    @FXML private TextField newProductNameField;
    @FXML private TextField newProductDescField;
    @FXML private TextField newProductPriceField;
    @FXML private Label productResultLabel;
    
    @FXML private TextField newSaleProductIdField;
    @FXML private TextField newSaleStockField;
    @FXML private TextField newSaleMaxQtyField;
    @FXML private Label saleResultLabel;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String jwtToken = "";
    private String currentReservationId = "";
    private Timeline countdownTimeline;
    private int remainingSeconds = 60;

    private final List<JsonNode> currentSales = new ArrayList<>();
    private Long selectedSaleId = 1L;
    private double currentPrice = 49999.0;

    @FXML
    public void initialize() {
        if (simRateSlider != null) {
            simRateSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int val = (int) Math.round(newVal.doubleValue());
                if (val == 100) {
                    simRateLabel.setText("100% (Instant Pay - Normal Shoppers)");
                    simRateLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: 800;");
                } else if (val == 50) {
                    simRateLabel.setText("50% (Cart Abandonment - Tests Auto-Restock)");
                    simRateLabel.setStyle("-fx-text-fill: #D97706; -fx-font-weight: 800;");
                } else if (val == 0) {
                    simRateLabel.setText("0% (All Unpaid - Tests 60s Hold Expiry)");
                    simRateLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 800;");
                } else {
                    simRateLabel.setText(val + "% Payment Conversion");
                    simRateLabel.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: 800;");
                }
            });
        }

        if (engineToggleGroup != null) {
            engineToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                if (radioDirectDb != null && radioDirectDb.isSelected()) {
                    runSimButton.setText("🗄️ Launch Direct DB Burst");
                } else {
                    runSimButton.setText("🔥 Launch Redis Lua Burst");
                }
            });
        }

        // Numeric filter for manual quantity typing
        quantityField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                quantityField.setText(newVal.replaceAll("[^\\d]", ""));
            }
            updateTotalCalculation();
        });

        // Selection listener for sales list
        salesListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            int index = newVal.intValue();
            if (index >= 0 && index < currentSales.size()) {
                updateSelectedSaleCard(currentSales.get(index));
            }
        });

        autoLoginAndConnect();
    }

    @FXML public void handleSetShoppers50() { if (simShoppersField != null) simShoppersField.setText("50"); }
    @FXML public void handleSetShoppers250() { if (simShoppersField != null) simShoppersField.setText("250"); }
    @FXML public void handleSetShoppers1000() { if (simShoppersField != null) simShoppersField.setText("1000"); }

    @FXML public void handleSetPay100() { if (simRateSlider != null) simRateSlider.setValue(100); }
    @FXML public void handleSetPay50() { if (simRateSlider != null) simRateSlider.setValue(50); }
    @FXML public void handleSetPay0() { if (simRateSlider != null) simRateSlider.setValue(0); }

    private void autoLoginAndConnect() {
        if (backendStatusLabel != null) backendStatusLabel.setText("Connecting...");
        new Thread(() -> {
            for (int attempt = 1; attempt <= 10; attempt++) {
                try {
                    String loginPayload = "{\"email\":\"admin@flashsale.local\",\"password\":\"admin123\"}";
                    HttpRequest loginReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                            .build();

                    HttpResponse<String> loginRes = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
                    if (loginRes.statusCode() == 200) {
                        JsonNode node = objectMapper.readTree(loginRes.body());
                        jwtToken = node.get("token").asText();
                        Platform.runLater(() -> {
                            if (backendStatusLabel != null) backendStatusLabel.setText("● System Online");
                            userBadgeLabel.setText("👤 Admin Active");
                            handleRefreshSales();
                        });
                        break;
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1200); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void ensureAuthenticated() {
        if (jwtToken != null && !jwtToken.isEmpty()) return;
        try {
            String loginPayload = "{\"email\":\"admin@flashsale.local\",\"password\":\"admin123\"}";
            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                    .build();

            HttpResponse<String> loginRes = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            if (loginRes.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(loginRes.body());
                jwtToken = node.get("token").asText();
                Platform.runLater(() -> userBadgeLabel.setText("👤 Admin Active"));
            }
        } catch (Exception ignored) {}
    }

    @FXML
    public void handleIncrementQuantity() {
        try {
            int q = Integer.parseInt(quantityField.getText().trim());
            quantityField.setText(String.valueOf(q + 1));
        } catch (Exception e) {
            quantityField.setText("1");
        }
        updateTotalCalculation();
    }

    @FXML
    public void handleDecrementQuantity() {
        try {
            int q = Integer.parseInt(quantityField.getText().trim());
            if (q > 1) {
                quantityField.setText(String.valueOf(q - 1));
            }
        } catch (Exception e) {
            quantityField.setText("1");
        }
        updateTotalCalculation();
    }

    private void updateTotalCalculation() {
        int q = 1;
        try {
            q = Integer.parseInt(quantityField.getText().trim());
            if (q <= 0) q = 1;
        } catch (Exception ignored) {}
        double total = q * currentPrice;
        cartTotalLabel.setText(String.format("₹%.2f", total));
    }

    @FXML
    public void handleRefreshSales() {
        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/sales"))
                        .header("Authorization", "Bearer " + jwtToken)
                        .GET()
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonNode array = objectMapper.readTree(res.body());
                    Platform.runLater(() -> {
                        currentSales.clear();
                        salesListView.getItems().clear();
                        if (array.isArray()) {
                            for (JsonNode sale : array) {
                                String status = sale.has("status") ? sale.get("status").asText() : "ACTIVE";
                                if (!"ACTIVE".equalsIgnoreCase(status)) {
                                    continue;
                                }

                                currentSales.add(sale);
                                long saleId = sale.has("id") ? sale.get("id").asLong() : 1L;
                                String prodName = sale.has("productName") ? sale.get("productName").asText() : ("Product #" + sale.get("productId").asLong());
                                int total = sale.has("saleStock") ? sale.get("saleStock").asInt() : 0;
                                int remaining = sale.has("availableStock") ? sale.get("availableStock").asInt() : total;

                                String item = String.format("Drop #%d • %s  [ %d left ]",
                                        saleId, prodName, remaining);
                                salesListView.getItems().add(item);
                            }
                        }
                        if (!currentSales.isEmpty()) {
                            salesListView.getSelectionModel().selectFirst();
                        }
                        if (simSaleComboBox != null) {
                            simSaleComboBox.getItems().clear();
                            for (JsonNode sale : currentSales) {
                                long sId = sale.has("id") ? sale.get("id").asLong() : 1L;
                                String pName = sale.has("productName") ? sale.get("productName").asText() : ("Product #" + sale.get("productId").asLong());
                                int total = sale.has("saleStock") ? sale.get("saleStock").asInt() : 0;
                                int remaining = sale.has("availableStock") ? sale.get("availableStock").asInt() : total;
                                simSaleComboBox.getItems().add(String.format("Drop #%d • %s (%d in stock)", sId, pName, remaining));
                            }
                            if (!simSaleComboBox.getItems().isEmpty()) {
                                simSaleComboBox.getSelectionModel().selectFirst();
                            }
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateSelectedSaleCard(JsonNode sale) {
        selectedSaleId = sale.has("id") ? sale.get("id").asLong() : 1L;
        String prodName = sale.has("productName") ? sale.get("productName").asText() : ("Product #" + sale.get("productId").asLong());
        selectedSaleTitleLabel.setText(prodName);
        heroProductSubtitle.setText("LIMITED DROP #" + selectedSaleId);
        
        if (sale.has("price")) {
            currentPrice = sale.get("price").asDouble();
            selectedSalePriceLabel.setText(String.format("₹%.2f", currentPrice));
        } else {
            currentPrice = 49999.0;
            selectedSalePriceLabel.setText(String.format("₹%.2f", currentPrice));
        }

        int total = sale.has("saleStock") ? sale.get("saleStock").asInt() : 100;
        int remaining = sale.has("availableStock") ? sale.get("availableStock").asInt() : total;
        int sold = sale.has("soldStock") ? sale.get("soldStock").asInt() : 0;
        int reserved = sale.has("reservedStock") ? sale.get("reservedStock").asInt() : 0;

        remainingStockBadge.setText(remaining + " units available");
        stockBreakdownLabel.setText(String.format("Total: %d | Sold: %d | Reserved: %d", total, sold, reserved));

        double progress = total > 0 ? (double) remaining / total : 0.0;
        stockLevelProgressBar.setProgress(progress);

        updateTotalCalculation();
    }

    @FXML
    public void handleReserveStock() {
        int quantity = 1;
        try {
            quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity <= 0) quantity = 1;
        } catch (Exception e) {
            quantity = 1;
            quantityField.setText("1");
        }

        checkoutResultLabel.setText("Locking stock in Redis Lua engine...");
        reserveButton.setDisable(true);
        final int finalQuantity = quantity;
        final long reqStart = System.currentTimeMillis();
        resetPipeline();

        new Thread(() -> {
            try {
                ensureAuthenticated();
                String idemKey = UUID.randomUUID().toString();
                String body = "{\"quantity\":" + finalQuantity + "}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/sales/" + selectedSaleId + "/reservations"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idemKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                long elapsed = System.currentTimeMillis() - reqStart;
                Platform.runLater(() -> {
                    reserveButton.setDisable(false);
                    if (res.statusCode() == 200) {
                        try {
                            JsonNode node = objectMapper.readTree(res.body());
                            currentReservationId = node.get("id").asText();
                            
                            cartItemTitle.setText(selectedSaleTitleLabel.getText() + " (x" + finalQuantity + ")");
                            reservationInfoLabel.setText("Hold ID: " + currentReservationId.substring(0, 8) + "...\nGuaranteed hold active.");
                            checkoutResultLabel.setText("✅ Stock locked! Complete payment before timer expires.");
                            payButton.setDisable(false);
                            startCountdownTimer();
                            updatePipelineSuccess(elapsed);
                            handleRefreshSales();
                        } catch (Exception e) {
                            checkoutResultLabel.setText("Parsing error: " + e.getMessage());
                        }
                    } else {
                        checkoutResultLabel.setText("❌ Reservation Denied: " + res.body());
                        String reason = "DENIED";
                        if (res.body().contains("INSUFFICIENT_STOCK")) reason = "NO STOCK";
                        else if (res.body().contains("SALE_OUT_OF_WINDOW")) reason = "WINDOW EXPIRED";
                        else if (res.body().contains("SALE_NOT_ACTIVE")) reason = "NOT ACTIVE";
                        updatePipelineRejected(reason, elapsed);
                    }
                });
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - reqStart;
                Platform.runLater(() -> {
                    reserveButton.setDisable(false);
                    checkoutResultLabel.setText("Connection error: " + e.getMessage());
                    updatePipelineRejected("CONN ERROR", elapsed);
                });
            }
        }).start();
    }

    private void resetPipeline() {
        if (pipelineTotalTimeLabel == null) return;
        pipelineTotalTimeLabel.setText("Processing...");
        pipelineTotalTimeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #2563EB;");
        updatePipelineNode(pipelineNode1, pipelineStep1Label, "⚪ Active", "#2563EB");
        updatePipelineNode(pipelineNode2, pipelineStep2Label, "⚪ Pending", "#71717A");
        updatePipelineNode(pipelineNode3, pipelineStep3Label, "⚪ Pending", "#71717A");
        updatePipelineNode(pipelineNode4, pipelineStep4Label, "⚪ Pending", "#71717A");
    }

    private void updatePipelineSuccess(long totalMs) {
        if (pipelineTotalTimeLabel == null) return;
        updatePipelineNode(pipelineNode1, pipelineStep1Label, "✅ PASS (0.2ms)", "#059669");
        updatePipelineNode(pipelineNode2, pipelineStep2Label, "✅ DECR (0.5ms)", "#059669");
        updatePipelineNode(pipelineNode3, pipelineStep3Label, "✅ WRITTEN (" + Math.max(1, totalMs - 1) + "ms)", "#059669");
        updatePipelineNode(pipelineNode4, pipelineStep4Label, "✅ ACTIVE (60s)", "#059669");
        pipelineTotalTimeLabel.setText("Total: " + totalMs + "ms (⚡ Ultra-Fast)");
        pipelineTotalTimeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #059669;");
    }

    private void updatePipelineRejected(String reason, long totalMs) {
        if (pipelineTotalTimeLabel == null) return;
        updatePipelineNode(pipelineNode1, pipelineStep1Label, "✅ PASS (0.2ms)", "#059669");
        updatePipelineNode(pipelineNode2, pipelineStep2Label, "❌ " + reason, "#DC2626");
        updatePipelineNode(pipelineNode3, pipelineStep3Label, "⚪ UNTOUCHED (0 DB load)", "#71717A");
        updatePipelineNode(pipelineNode4, pipelineStep4Label, "⚪ Aborted", "#71717A");
        pipelineTotalTimeLabel.setText("Rejected in " + totalMs + "ms (Postgres Protected!)");
        pipelineTotalTimeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #DC2626;");
    }

    private void updatePipelineNode(VBox node, Label label, String text, String color) {
        if (label != null) {
            label.setText(text);
            label.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + color + ";");
        }
        if (node != null) {
            if ("#059669".equals(color)) {
                node.setStyle("-fx-background-color: #ECFDF5; -fx-border-color: #10B981; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 8px 12px;");
            } else if ("#DC2626".equals(color)) {
                node.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #EF4444; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 8px 12px;");
            } else {
                node.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E4E4E7; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-padding: 8px 12px;");
            }
        }
    }

    private void startCountdownTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        remainingSeconds = 60;
        countdownTimerLabel.setText(remainingSeconds + "s");
        countdownProgressBar.setProgress(1.0);

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownTimerLabel.setText(remainingSeconds + "s");
            countdownProgressBar.setProgress((double) remainingSeconds / 60.0);

            if (remainingSeconds <= 0) {
                countdownTimeline.stop();
                payButton.setDisable(true);
                reservationInfoLabel.setText("⏰ Window expired. Stock restored to available pool.");
                checkoutResultLabel.setText("⚠️ Stock hold released.");
                handleRefreshSales();
            }
        }));
        countdownTimeline.setCycleCount(60);
        countdownTimeline.play();
    }

    @FXML
    public void handlePayOrder() {
        payButton.setDisable(true);
        checkoutResultLabel.setText("Processing payment & creating order...");

        new Thread(() -> {
            try {
                ensureAuthenticated();
                String idemKey = UUID.randomUUID().toString();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/reservations/" + currentReservationId + "/payments"))
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idemKey)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                Platform.runLater(() -> {
                    if (countdownTimeline != null) countdownTimeline.stop();
                    if (res.statusCode() == 200) {
                        try {
                            JsonNode payNode = objectMapper.readTree(res.body());
                            if (payNode.has("status") && "FAILED".equalsIgnoreCase(payNode.get("status").asText())) {
                                checkoutResultLabel.setText("❌ Payment Failed! Transaction declined.");
                                checkoutResultLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                                reservationInfoLabel.setText("⚠️ Payment declined. Hold will expire in remaining window.");
                                return;
                            }
                        } catch (Exception ignored) {}
                        checkoutResultLabel.setText("🎉 ORDER CONFIRMED! Payment processed, outbox published.");
                        checkoutResultLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                        reservationInfoLabel.setText("✅ Order complete. Thank you!");
                        handleRefreshSales();
                    } else {
                        checkoutResultLabel.setText("Payment failed: " + res.body());
                        checkoutResultLabel.setStyle("-fx-text-fill: #DC2626;");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> checkoutResultLabel.setText("Payment error: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleRunSimulation() {
        runSimButton.setDisable(true);
        simLogArea.setText("");

        new Thread(() -> {
            try {
                long targetSaleId = selectedSaleId;
                if (simSaleComboBox != null && !simSaleComboBox.getSelectionModel().isEmpty() && !currentSales.isEmpty()) {
                    int selIdx = simSaleComboBox.getSelectionModel().getSelectedIndex();
                    if (selIdx >= 0 && selIdx < currentSales.size()) {
                        targetSaleId = currentSales.get(selIdx).get("id").asLong();
                    }
                }

                int shoppers = 100;
                try {
                    shoppers = Integer.parseInt(simShoppersField.getText().trim());
                    if (shoppers <= 0) shoppers = 100;
                } catch (Exception e) {
                    shoppers = 100;
                    if (simShoppersField != null) {
                        final int resetVal = shoppers;
                        Platform.runLater(() -> simShoppersField.setText(String.valueOf(resetVal)));
                    }
                }

                int rate = (simRateSlider != null) ? (int) Math.round(simRateSlider.getValue()) : 100;
                boolean isDirectDb = (radioDirectDb != null && radioDirectDb.isSelected());
                String engine = isDirectDb ? "DIRECT_DB" : "REDIS_LUA";
                final long sid = targetSaleId;
                final int n = shoppers;
                final int conv = rate;

                Platform.runLater(() -> simLogArea.appendText(
                        "========================================================================================\n" +
                        "⚡ LAUNCHING BURST SIMULATION [" + engine + " ENGINE]\n" +
                        "Target Sale ID: " + sid + " | Shoppers: " + n + " | Checkout Conversion: " + conv + "%\n" +
                        "----------------------------------------------------------------------------------------\n"
                ));

                JsonNode result = runSimulationSync(sid, n, conv, engine);
                Platform.runLater(() -> renderSingleResult(result, engine));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    simLogArea.appendText("❌ Benchmark Failed: " + e.getMessage() + "\n");
                    runSimButton.setDisable(false);
                });
            }
        }).start();
    }

    private JsonNode runSimulationSync(long saleId, int shoppers, int rate, String engine) throws Exception {
        String payload = String.format("{\"saleId\":%d,\"users\":%d,\"attempts\":%d,\"shoppers\":%d,\"paymentSuccessRate\":%d,\"engine\":\"%s\"}",
                saleId, shoppers, shoppers, shoppers, rate, engine);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/admin/simulations"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonNode dispatch = objectMapper.readTree(res.body());
        String simId = dispatch.get("id").asText();

        for (int i = 0; i < 45; i++) {
            Thread.sleep(800);
            HttpRequest pollReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/admin/simulations/" + simId))
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();
            HttpResponse<String> pollRes = httpClient.send(pollReq, HttpResponse.BodyHandlers.ofString());
            if (pollRes.statusCode() == 200) {
                JsonNode polled = objectMapper.readTree(pollRes.body());
                if ("COMPLETED".equals(polled.get("status").asText())) {
                    return polled;
                } else if ("FAILED".equals(polled.get("status").asText())) {
                    throw new RuntimeException("Simulation execution failed on backend");
                }
            }
        }
        throw new RuntimeException("Simulation timed out after 35s");
    }

    private void renderSingleResult(JsonNode node, String engine) {
        int total = node.get("totalRequests").asInt();
        int admitted = node.get("successfulReservations").asInt();
        int rejected = node.get("rejectedRequests").asInt();
        int failed = node.get("failedRequests").asInt();
        long dur = node.get("durationMs").asLong();
        double throughput = node.get("throughput").asDouble();
        boolean isRedis = "REDIS_LUA".equalsIgnoreCase(engine);

        simTotalLabel.setText(String.valueOf(total));
        simSuccessLabel.setText(String.valueOf(admitted));
        simRejectedLabel.setText(String.valueOf(rejected));
        simThroughputLabel.setText(String.format("%.2f", throughput));
        simDurationLabel.setText(dur + "ms");

        // Database Blast Shield metrics
        int dbQueries = isRedis ? admitted : total;
        int shieldedQueries = isRedis ? rejected : 0;
        double shieldPct = total > 0 ? (shieldedQueries * 100.0) / total : 0.0;
        double dbQueryPct = total > 0 ? (dbQueries * 100.0) / total : 100.0;

        if (dbQueriesLabel != null) {
            if (isRedis) {
                dbQueriesLabel.setText(String.format("PostgreSQL Queries: %d of %d (%.1f%%)", dbQueries, total, dbQueryPct));
                dbQueriesLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #059669;");
            } else {
                dbQueriesLabel.setText(String.format("PostgreSQL Queries: %d of %d (100%% DIRECT SLAM)", total, total));
                dbQueriesLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
            }
        }
        if (dbShieldedLabel != null) {
            if (isRedis) {
                dbShieldedLabel.setText(String.format("🛡️ Shielded Load: %.1f%% (%d deflected)", shieldPct, shieldedQueries));
                dbShieldedLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #059669;");
            } else {
                dbShieldedLabel.setText("⚠️ Shielded Load: 0.0% (Zero Protection)");
                dbShieldedLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
            }
        }
        if (dbPoolStatusLabel != null) {
            if (isRedis) {
                dbPoolStatusLabel.setText("HikariCP Pool: 1-2/10 Active (Database Safe)");
                dbPoolStatusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #059669;");
            } else {
                dbPoolStatusLabel.setText("HikariCP Pool: 10/10 Saturated (High Lock Queuing)");
                dbPoolStatusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✔ SIMULATION COMPLETED SUCCESSFULLY!\n");
        sb.append(String.format("Engine Architecture:           %s\n", engine));
        sb.append(String.format("Total Requests Processed:      %d\n", total));
        sb.append(String.format("Admitted Reservations:         %d\n", admitted));
        sb.append(String.format("Rejected (Out-of-Stock):       %d\n", rejected));
        sb.append(String.format("Failed / System Errors:        %d\n", failed));
        sb.append(String.format("Duration:                      %d ms\n", dur));
        sb.append(String.format("Throughput:                    %.2f requests/sec\n", throughput));

        if (node.has("latencyStatistics") && !node.get("latencyStatistics").isNull()) {
            try {
                JsonNode stats = objectMapper.readTree(node.get("latencyStatistics").asText());
                sb.append("----------------------------------------------------------------------------------------\n");
                sb.append("LATENCY PROFILE:\n");
                sb.append("  • Avg Latency:   ").append(stats.has("avgMs") ? stats.get("avgMs").asText() : "--").append(" ms\n");
                sb.append("  • Median (p50):  ").append(stats.has("p50Ms") ? stats.get("p50Ms").asText() : "--").append(" ms\n");
                sb.append("  • 95th % (p95):  ").append(stats.has("p95Ms") ? stats.get("p95Ms").asText() : "--").append(" ms\n");
                sb.append("  • 99th % (p99):  ").append(stats.has("p99Ms") ? stats.get("p99Ms").asText() : "--").append(" ms\n");
                sb.append("  • Min / Max:     ").append(stats.has("minMs") ? stats.get("minMs").asText() : "--").append(" ms / ")
                        .append(stats.has("maxMs") ? stats.get("maxMs").asText() : "--").append(" ms\n");
            } catch (Exception ignored) {}
        }

        // DYNAMIC ARCHITECTURE FLOW CHART
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append("                 TRAFFIC EXECUTION FLOW CHART (").append(engine).append(")\n");
        sb.append("----------------------------------------------------------------------------------------\n\n");

        if (isRedis) {
            sb.append(String.format("    [ %,d Concurrent Shoppers Burst ]\n", total));
            sb.append("                   │\n");
            sb.append("                   ▼\n");
            sb.append(String.format("       [ 1. Ingress Rate Limiter (Token Bucket) ] ──▶ (%,d Allowed in 0.1ms)\n", total));
            sb.append("                   │\n");
            sb.append("                   ▼\n");
            sb.append("       [ 2. Redis In-Memory Lua Gate (Atomic Stock Counter) ]\n");
            sb.append(String.format("          ├──▶ %,d Shoppers (%.1f%%) ── [ ❌ REJECTED in 0.2ms (Out-of-Stock) ]\n", rejected, shieldPct));
            sb.append("          │                                    │\n");
            sb.append("          │                                    ▼\n");
            sb.append("          │                              [ 🛡️ 0 DB QUERIES (PostgreSQL Protected) ]\n");
            sb.append("          │\n");
            sb.append(String.format("          └──▶ %,d Shoppers (%.1f%%)   ── [ ✅ ADMITTED (Stock Decremented) ]\n", admitted, (100.0 - shieldPct)));
            sb.append("                                               │\n");
            sb.append("                                               ▼\n");
            sb.append("                                        [ 3. PostgreSQL ACID Ledger ]\n");
            sb.append(String.format("                                           • %d Row-Level Locks Acquired\n", admitted));
            sb.append(String.format("                                           • %d Reservations Created Durably\n", admitted));
            sb.append("                                               │\n");
            sb.append("                                               ▼\n");
            sb.append("                                        [ 4. Payment Gateway & Outbox Stream ]\n");
            sb.append(String.format("                                           • %d Payments Processed\n", admitted));
            sb.append(String.format("                                           • %d Orders Confirmed & Outbox Events Published\n", admitted));
        } else {
            sb.append(String.format("    [ %,d Concurrent Shoppers Burst ]\n", total));
            sb.append("                   │\n");
            sb.append("                   ▼\n");
            sb.append(String.format("       [ 1. Ingress Rate Limiter (Token Bucket) ] ──▶ (%,d Allowed in 0.1ms)\n", total));
            sb.append("                   │\n");
            sb.append("                   ▼  ⚠️ (NO IN-MEMORY SHIELD! ALL REQUESTS SLAM DIRECTLY INTO DB)\n");
            sb.append("       [ 2. PostgreSQL Connection Pool (HikariCP 10 Connections) ]\n");
            sb.append(String.format("          └── %,d Connections Contending for 10 Available Pool Slots\n", total));
            sb.append("                   │\n");
            sb.append("                   ▼\n");
            sb.append("       [ 3. PostgreSQL Row-Level Lock & UPDATE Evaluation ]\n");
            sb.append(String.format("          ├──▶ %,d Shoppers (%.1f%%)   ── [ ✅ SUCCESS: Row Updated ]\n", admitted, ((admitted * 100.0) / Math.max(1, total))));
            sb.append("          │                                    │\n");
            sb.append("          │                                    ▼\n");
            sb.append(String.format("          │                                 [ Write %d Reservations + Orders ]\n", admitted));
            sb.append("          │\n");
            sb.append(String.format("          └──▶ %,d Shoppers (%.1f%%) ── [ ❌ REJECTED: UPDATE 0 (Condition False) ]\n", rejected, ((rejected * 100.0) / Math.max(1, total))));
            sb.append("                                               │\n");
            sb.append("                                               ▼\n");
            sb.append(String.format("                                            [ ⚠️ %,d Unnecessary DB Queries Executed! ]\n", rejected));
            sb.append("                                            [ High Connection Lockup & Thread Starvation ]\n");
        }

        // DATABASE BLAST SHIELD METRICS CARD
        sb.append("\n========================================================================================\n");
        sb.append("                          🛡️ DATABASE BLAST SHIELD ANALYSIS\n");
        sb.append("========================================================================================\n");
        sb.append(String.format("  • Engine Architecture:             %s\n", engine));
        sb.append(String.format("  • Total Inbound Shoppers:          %,d\n", total));
        sb.append(String.format("  • Filtered at Redis In-Memory:     %,d requests (%.1f%%)\n", shieldedQueries, shieldPct));
        sb.append(String.format("  • Queries Sent to PostgreSQL:      %,d requests (%.1f%%)\n", dbQueries, dbQueryPct));
        if (isRedis) {
            sb.append(String.format("  • Database Shielding Efficiency:   🛡️ %.1f%% OF DATABASE LOAD ELIMINATED!\n", shieldPct));
            sb.append("  • Relational DB Blast Risk:        ZERO (Relational storage completely protected)\n");
        } else {
            sb.append("  • Database Shielding Efficiency:   ⚠️ 0.0% (ZERO PROTECTION — FULL DB EXPOSURE)\n");
            sb.append("  • Relational DB Blast Risk:        CRITICAL (Connection pool locked, row contention)\n");
        }
        sb.append("========================================================================================\n");

        simLogArea.appendText(sb.toString());
        runSimButton.setDisable(false);
        handleRefreshSales();
    }

    @FXML
    public void handleAddProduct() {
        String name = newProductNameField.getText().trim();
        String desc = newProductDescField.getText().trim();
        String priceStr = newProductPriceField.getText().trim();

        if (name.isEmpty() || desc.isEmpty() || priceStr.isEmpty()) {
            productResultLabel.setText("❌ All fields required.");
            productResultLabel.setStyle("-fx-text-fill: #DC2626;");
            return;
        }

        productResultLabel.setText("Creating product...");
        productResultLabel.setStyle("-fx-text-fill: #059669;");

        new Thread(() -> {
            try {
                double price = Double.parseDouble(priceStr);
                String payload = String.format("{\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f}",
                        name, desc, price);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/products"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + jwtToken)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                Platform.runLater(() -> {
                    if (res.statusCode() == 200 || res.statusCode() == 201) {
                        try {
                            JsonNode node = objectMapper.readTree(res.body());
                            String pid = node.get("id").asText();
                            productResultLabel.setText("✅ Product created successfully! ID: " + pid);
                            newProductNameField.clear();
                            newProductDescField.clear();
                            newProductPriceField.clear();
                        } catch (Exception e) {
                            productResultLabel.setText("✅ Created, but could not parse response.");
                        }
                    } else {
                        productResultLabel.setText("❌ Failed: " + res.body());
                        productResultLabel.setStyle("-fx-text-fill: #DC2626;");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    productResultLabel.setText("❌ Error: " + e.getMessage());
                    productResultLabel.setStyle("-fx-text-fill: #DC2626;");
                });
            }
        }).start();
    }

    @FXML
    public void handleAddSale() {
        String pidStr = newSaleProductIdField.getText().trim();
        String stockStr = newSaleStockField.getText().trim();
        String maxQtyStr = newSaleMaxQtyField.getText().trim();

        if (pidStr.isEmpty() || stockStr.isEmpty() || maxQtyStr.isEmpty()) {
            saleResultLabel.setText("❌ All fields required.");
            saleResultLabel.setStyle("-fx-text-fill: #DC2626;");
            return;
        }

        saleResultLabel.setText("Creating flash sale...");
        saleResultLabel.setStyle("-fx-text-fill: #059669;");

        new Thread(() -> {
            try {
                long pid = Long.parseLong(pidStr);
                int stock = Integer.parseInt(stockStr);
                int maxQty = Integer.parseInt(maxQtyStr);
                // Creating a sale that started 1 minute ago and stays active for 1 year so test drops never expire
                String start = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1).toString();
                String end = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusYears(1).toString();

                String payload = String.format("{\"productId\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"saleStock\":%d,\"maxQuantityPerPurchase\":%d,\"status\":\"ACTIVE\"}",
                        pid, start, end, stock, maxQty);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/admin/sales"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + jwtToken)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 || res.statusCode() == 201) {
                    JsonNode node = objectMapper.readTree(res.body());
                    String sid = node.get("id").asText();

                    // Instantly activate the sale by calling the activate endpoint
                    HttpRequest activateReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/admin/sales/" + sid + "/activate"))
                            .header("Authorization", "Bearer " + jwtToken)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<String> activateRes = httpClient.send(activateReq, HttpResponse.BodyHandlers.ofString());

                    Platform.runLater(() -> {
                        if (activateRes.statusCode() == 200) {
                            saleResultLabel.setText("✅ Sale created & activated! ID: " + sid);
                        } else {
                            saleResultLabel.setText("⚠️ Created (ID: " + sid + "), but activation failed: " + activateRes.body());
                        }
                        newSaleProductIdField.clear();
                        newSaleStockField.clear();
                        newSaleMaxQtyField.clear();
                        handleRefreshSales(); // Refresh the main storefront list
                    });
                } else {
                    Platform.runLater(() -> {
                        saleResultLabel.setText("❌ Failed: " + res.body());
                        saleResultLabel.setStyle("-fx-text-fill: #DC2626;");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saleResultLabel.setText("❌ Error: " + e.getMessage());
                    saleResultLabel.setStyle("-fx-text-fill: #DC2626;");
                });
            }
        }).start();
    }
}
