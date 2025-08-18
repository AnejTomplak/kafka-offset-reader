package com.example.kafkaoffsetreader;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
public class KafkaReaderController {
    
    @Autowired
    private KafkaReaderService kafkaReaderService;
    
    @Autowired 
    private KafkaConnectionPool connectionPool;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    // Topic metadata cache to avoid repeated AdminClient calls
    private final Map<String, TopicMetadata> topicMetadataCache = new ConcurrentHashMap<>();
    private static final long METADATA_CACHE_TTL = 60000; // 1 minute TTL

    // Inner class for caching topic metadata
    private static class TopicMetadata {
        final Set<Integer> partitions;
        final long timestamp;
        
        TopicMetadata(Set<Integer> partitions) {
            this.partitions = partitions;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > METADATA_CACHE_TTL;
        }
    }

    /**
     * Validate partition exists for topic to prevent endless retry loops
     */
    private CompletableFuture<Boolean> validatePartitionExists(String topic, int partition) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                TopicMetadata cached = topicMetadataCache.get(topic);
                if (cached != null && !cached.isExpired()) {
                    return cached.partitions.contains(partition);
                }

                // Get topic metadata using AdminClient from connection pool
                Properties adminProps = connectionPool.getAdminProperties();
                try (AdminClient adminClient = AdminClient.create(adminProps)) {
                    
                    DescribeTopicsResult result = adminClient.describeTopics(Arrays.asList(topic));
                    TopicDescription topicDesc = result.topicNameValues().get(topic).get(5, TimeUnit.SECONDS);
                    
                    // Extract partition numbers
                    Set<Integer> partitions = new HashSet<>();
                    for (TopicPartitionInfo partInfo : topicDesc.partitions()) {
                        partitions.add(partInfo.partition());
                    }
                    
                    // Update cache
                    topicMetadataCache.put(topic, new TopicMetadata(partitions));
                    
                    return partitions.contains(partition);
                }
            } catch (ExecutionException | TimeoutException | InterruptedException e) {
                // If validation fails, assume partition exists to avoid blocking
                // Log warning but continue processing
                System.err.println("Warning: Could not validate partition " + partition + 
                                 " for topic " + topic + ": " + e.getMessage());
                return true;
            }
        });
    }

    /**
     * Main Kafka REST API endpoint with async high-performance implementation
     * Supports rack-aware consumer fetching and connection pooling
     * Now includes partition validation to prevent endless retry loops
     */
    @GetMapping("/topics/{topic}/partitions/{partition}/messages")
    public CompletableFuture<ResponseEntity<Object>> readMessages(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) String clientRack
    ) {
        // First validate partition exists to prevent endless retry loops
        return validatePartitionExists(topic, partition)
            .thenCompose(partitionExists -> {
                if (!partitionExists) {
                    // Return 404 with error details for non-existent partition
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Partition " + partition + " does not exist for topic " + topic);
                    error.put("topic", topic);
                    error.put("partition", partition);
                    return CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
                    );
                }
                
                // Partition exists, proceed with reading messages
                return kafkaReaderService.readAsync(topic, partition, offset, count, clientRack)
                    .thenApply(messages -> ResponseEntity.ok((Object) messages))
                    .exceptionally(ex -> {
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "Failed to read messages: " + ex.getMessage());
                        error.put("topic", topic);
                        error.put("partition", partition);
                        error.put("offset", offset);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
                    });
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
        // First validate partition exists
        return validatePartitionExists(topic, partition)
            .thenCompose(partitionExists -> {
                if (!partitionExists) {
                    // Return 404 with error details for non-existent partition
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Partition " + partition + " does not exist for topic " + topic);
                    error.put("topic", topic);
                    error.put("partition", partition);
                    return CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
                    );
                }
                
                // Partition exists, proceed with getting offsets
                return kafkaReaderService.getOffsetsAsync(topic, partition, clientRack)
                    .thenApply(offsets -> ResponseEntity.ok((Object) offsets))
                    .exceptionally(ex -> {
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "Failed to get offsets: " + ex.getMessage());
                        error.put("topic", topic);
                        error.put("partition", partition);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
                    });
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