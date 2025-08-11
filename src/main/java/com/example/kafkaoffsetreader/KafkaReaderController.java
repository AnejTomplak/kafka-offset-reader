package com.example.kafkaoffsetreader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class KafkaReaderController {
    
    @Autowired
    private KafkaReaderService kafkaReaderService;
    
    @Autowired 
    private KafkaConnectionPool connectionPool;

    /**
     * Main Kafka REST API endpoint with async high-performance implementation
     * Supports rack-aware consumer fetching and connection pooling
     */
    @GetMapping("/topics/{topic}/partitions/{partition}/messages")
    public CompletableFuture<List<String>> readMessages(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) String clientRack
    ) {
        return kafkaReaderService.readAsync(topic, partition, offset, count, clientRack);
    }
    
    /**
     * Connection pool monitoring endpoint
     */
    @GetMapping("/monitoring/pool-stats")
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("connectionPools", connectionPool.getPoolStats());
        stats.put("serviceHealth", kafkaReaderService.isHealthy());
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", kafkaReaderService.isHealthy() ? "UP" : "DOWN");
        health.put("connectionPools", connectionPool.getPoolStats());
        return health;
    }
}

