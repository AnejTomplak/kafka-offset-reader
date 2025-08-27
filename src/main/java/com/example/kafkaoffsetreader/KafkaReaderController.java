package com.example.kafkaoffsetreader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
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
     * Includes consumer-based partition validation for performance
     */
    @GetMapping("/topics/{topic}/partitions/{partition}/messages")
    public CompletableFuture<ResponseEntity<Object>> readMessages(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) String clientRack
    ) {
        // Use service-level partition validation for performance
        return kafkaReaderService.readAsync(topic, partition, offset, count, clientRack)
            .thenApply(messages -> ResponseEntity.ok((Object) messages))
            .exceptionally(ex -> {
                // Handle invalid partition exceptions
                if (ex.getCause() instanceof IllegalArgumentException) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", ex.getCause().getMessage());
                    error.put("topic", topic);
                    error.put("partition", partition);
                    error.put("offset", offset);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body((Object) error);
                }
                
                // Handle other exceptions
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Failed to read messages: " + ex.getMessage());
                error.put("topic", topic);
                error.put("partition", partition);
                error.put("offset", offset);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body((Object) error);
            });
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
     * Kafka REST API - Get partition offsets with validation
     * GET /topics/{topic}/partitions/{partition}/offsets
     */
    @GetMapping("/topics/{topic}/partitions/{partition}/offsets")
    public CompletableFuture<ResponseEntity<Object>> getPartitionOffsets(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam(required = false) String clientRack
    ) {
        // Use consumer-based validation directly in service
        return kafkaReaderService.getOffsetsAsync(topic, partition, clientRack)
            .thenApply(offsets -> {
                if (offsets == null) {
                    // Invalid partition detected by consumer metadata
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Partition " + partition + " does not exist for topic " + topic);
                    error.put("topic", topic);
                    error.put("partition", partition);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body((Object) error);
                }
                return ResponseEntity.ok((Object) offsets);
            })
            .exceptionally(ex -> {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Failed to get offsets: " + ex.getMessage());
                error.put("topic", topic);
                error.put("partition", partition);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body((Object) error);
            });
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