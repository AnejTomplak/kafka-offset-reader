package com.example.kafkaoffsetreader;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka Reader Service
 * 
 * Uses connection pooling and async processing to handle
 * high-volume concurrent requests efficiently.
 * 
 * Features:
 * - Connection pooling (no TCP overhead per request)
 * - Async processing support
 * - Thread-safe operations
 * - Better error handling and monitoring
 * - Rack-aware consumer fetching
 */
@Service
public class KafkaReaderService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaReaderService.class);
    
    @Autowired
    private KafkaConnectionPool connectionPool;
    
    /**
     * Synchronous read method for compatibility
     */
    public List<String> read(String topic, int partition, long offset, int count) {
        return read(topic, partition, offset, count, null);
    }
    
    /**
     * Synchronous read with rack preference
     */
    public List<String> read(String topic, int partition, long offset, int count, String clientRack) {
        long startTime = System.currentTimeMillis();
        List<String> results = new ArrayList<>();
        KafkaConsumer<String, String> consumer = null;
        
        try {
            // Borrow consumer from pool
            consumer = connectionPool.borrowConsumer(clientRack);
            
            TopicPartition tp = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(tp));
            
            // Handle special offsets: -1 = beginning, -2 = end
            long effectiveOffset = offset;
            if (offset == -1) {
                effectiveOffset = consumer.beginningOffsets(Collections.singletonList(tp)).getOrDefault(tp, 0L);
            } else if (offset == -2) {
                effectiveOffset = consumer.endOffsets(Collections.singletonList(tp)).getOrDefault(tp, 0L);
            } else if (offset < 0) {
                throw new IllegalArgumentException("Invalid offset: " + offset + ". Use -1 for beginning, -2 for end, or >= 0 for specific offset");
            }
            
            consumer.seek(tp, effectiveOffset);
            
            logger.debug("Reading from topic={} partition={} offset={} (effective={}) rack={}", 
                topic, partition, offset, effectiveOffset, clientRack != null ? clientRack : "default");
            
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            
            for (ConsumerRecord<String, String> record : records) {
                results.add(record.value());
                if (results.size() >= count) break;
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Retrieved {} records in {}ms (rack={}, from offset={})", 
                results.size(), duration, clientRack != null ? clientRack : "default", effectiveOffset);
            
        } catch (Exception e) {
            logger.error("Error reading from Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read from Kafka", e);
        } finally {
            // Always return consumer to pool
            if (consumer != null) {
                connectionPool.returnConsumer(consumer, clientRack);
            }
        }
        
        return results;
    }
    
    /**
     * Asynchronous read method for high-throughput scenarios
     */
    @Async
    public CompletableFuture<List<String>> readAsync(String topic, int partition, long offset, int count, String clientRack) {
        try {
            List<String> result = read(topic, partition, offset, count, clientRack);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<List<String>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
 * Get partition beginning and end offsets
 */
    @Async
    public CompletableFuture<Map<String, Long>> getOffsetsAsync(String topic, int partition, String clientRack) {
        KafkaConsumer<String, String> consumer = null;
        try {
            consumer = connectionPool.borrowConsumer(clientRack);
            
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(topicPartition));
            
            long beginningOffset = consumer.beginningOffsets(Collections.singletonList(topicPartition))
                .get(topicPartition);
            long endOffset = consumer.endOffsets(Collections.singletonList(topicPartition))
                .get(topicPartition);
            
            Map<String, Long> result = new HashMap<>();
            result.put("beginning_offset", beginningOffset);
            result.put("end_offset", endOffset);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get offsets for " + topic + "-" + partition, e);
        } finally {
            if (consumer != null) {
                connectionPool.returnConsumer(consumer, clientRack);
            }
        }
    }

}


