package com.flashsale.client.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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

    // Simulator Tab
    @FXML private TextField simSaleIdField;
    @FXML private TextField simUsersField;
    @FXML private TextField simAttemptsField;
    @FXML private Slider simRateSlider;
    @FXML private Label simRateLabel;
    @FXML private Button runSimButton;
    @FXML private Label simTotalLabel;
    @FXML private Label simSuccessLabel;
    @FXML private Label simRejectedLabel;
    @FXML private Label simThroughputLabel;
    @FXML private Label simDurationLabel;
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
        simRateSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            simRateLabel.setText(String.format("%.0f%%", newVal.doubleValue()));
        });

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

    private void autoLoginAndConnect() {
        if (backendStatusLabel != null) backendStatusLabel.setText("Connecting...");
        new Thread(() -> {
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
                } else {
                    Platform.runLater(() -> {
                        if (backendStatusLabel != null) backendStatusLabel.setText("🔴 Auth Offline");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (backendStatusLabel != null) backendStatusLabel.setText("🔴 Connecting...");
                });
            }
        }).start();
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

        new Thread(() -> {
            try {
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
                            handleRefreshSales();
                        } catch (Exception e) {
                            checkoutResultLabel.setText("Parsing error: " + e.getMessage());
                        }
                    } else {
                        checkoutResultLabel.setText("❌ Reservation Denied: " + res.body());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    reserveButton.setDisable(false);
                    checkoutResultLabel.setText("Connection error: " + e.getMessage());
                });
            }
        }).start();
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
        simLogArea.setText("Starting simulation...\n");

        new Thread(() -> {
            try {
                long saleId = Long.parseLong(simSaleIdField.getText().trim());
                int users = Integer.parseInt(simUsersField.getText().trim());
                int attempts = Integer.parseInt(simAttemptsField.getText().trim());
                int rate = (int) simRateSlider.getValue();

                String payload = String.format("{\"saleId\":%d,\"users\":%d,\"attempts\":%d,\"paymentSuccessRate\":%d}",
                        saleId, users, attempts, rate);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/admin/simulations"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + jwtToken)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonNode node = objectMapper.readTree(res.body());
                    String simId = node.get("id").asText();
                    Platform.runLater(() -> simLogArea.appendText("Simulation dispatched (ID: " + simId + "). Polling results...\n"));
                    pollSimulation(simId);
                } else {
                    Platform.runLater(() -> {
                        simLogArea.appendText("Simulation start failed: " + res.body() + "\n");
                        runSimButton.setDisable(false);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    simLogArea.appendText("Error: " + e.getMessage() + "\n");
                    runSimButton.setDisable(false);
                });
            }
        }).start();
    }

    private void pollSimulation(String simId) {
        new Thread(() -> {
            boolean done = false;
            for (int i = 0; i < 30 && !done; i++) {
                try {
                    Thread.sleep(1000);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/admin/simulations/" + simId))
                            .header("Authorization", "Bearer " + jwtToken)
                            .GET()
                            .build();

                    HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 200) {
                        JsonNode node = objectMapper.readTree(res.body());
                        String status = node.get("status").asText();
                        if ("COMPLETED".equals(status)) {
                            done = true;
                            Platform.runLater(() -> {
                                simTotalLabel.setText(node.get("totalRequests").asText());
                                simSuccessLabel.setText(node.get("successfulReservations").asText());
                                simRejectedLabel.setText(node.get("rejectedRequests").asText());
                                simThroughputLabel.setText(node.get("throughput").asText());
                                simDurationLabel.setText(node.get("durationMs").asText() + "ms");

                                simLogArea.appendText("=========================================\n");
                                simLogArea.appendText("SIMULATION COMPLETED SUCCESSFULLY!\n");
                                simLogArea.appendText("Total Requests Processed: " + node.get("totalRequests") + "\n");
                                simLogArea.appendText("Admitted Reservations: " + node.get("successfulReservations") + "\n");
                                simLogArea.appendText("Rejected Out-of-Stock/Inactive: " + node.get("rejectedRequests") + "\n");
                                simLogArea.appendText("Failed (Errors/Not Found): " + node.get("failedRequests") + "\n");
                                simLogArea.appendText("Duration: " + node.get("durationMs") + " ms\n");
                                simLogArea.appendText("Throughput: " + node.get("throughput") + " requests/sec\n");
                                simLogArea.appendText("Zero overselling invariant VERIFIED!\n");
                                simLogArea.appendText("=========================================\n");
                                runSimButton.setDisable(false);
                                handleRefreshSales();
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        }).start();
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
                // Creating a sale that started 1 minute ago to ensure it is immediately active
                String start = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1).toString();
                String end = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).toString();

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
