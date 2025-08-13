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

    @Autowired
    private KafkaProducerService kafkaProducerService;

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
     * Kafka REST API - Send messages to topic
     * POST /topics/{topic}
     */
    @PostMapping("/topics/{topic}")
    public CompletableFuture<Map<String, Object>> sendMessages(
        @PathVariable String topic,
        @RequestBody Map<String, Object> request,
        @RequestParam(required = false) String clientRack
    ) {
        return kafkaProducerService.sendAsync(topic, request, clientRack);
    }

    /**
     * Kafka REST API - Get partition offsets
     * GET /topics/{topic}/partitions/{partition}/offsets
     */
    @GetMapping("/topics/{topic}/partitions/{partition}/offsets")
    public CompletableFuture<Map<String, Long>> getPartitionOffsets(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam(required = false) String clientRack
    ) {
        return kafkaReaderService.getOffsetsAsync(topic, partition, clientRack);
    }

    /**
     * Monitoring endpoint for connection pool stats
     */
    @GetMapping("/monitoring/pool-stats")
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pools", connectionPool.getPoolStats());
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }
}