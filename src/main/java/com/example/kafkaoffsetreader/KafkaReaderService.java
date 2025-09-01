package com.example.kafkaoffsetreader;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public List<Map<String, Object>> read(String topic, int partition, long offset, int count) {
        return read(topic, partition, offset, count, null);
    }
    
    /**
     * Synchronous read with rack preference
     */
    public List<Map<String, Object>> read(String topic, int partition, long offset, int count, String clientRack) {
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        KafkaConsumer<String, byte[]> consumer = null;
        
        try {
            // Borrow consumer from pool
            consumer = connectionPool.borrowConsumer(clientRack);
            
            // Validate partition exists using consumer metadata
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
            if (partitionInfos == null || partition >= partitionInfos.size()) {
                // Invalid partition - throw exception to be handled by controller
                throw new IllegalArgumentException("Partition " + partition + " does not exist for topic " + topic);
            }
            
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
            
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            
            for (ConsumerRecord<String, byte[]> record : records) {
                // Create response object in Kafka REST proxy format - topic, key, value, partition, offset order
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("topic", record.topic());
                
                // Encode key as base64 if exists
                if (record.key() != null) {
                    message.put("key", Base64.getEncoder().encodeToString(record.key().getBytes()));
                } else {
                    message.put("key", null);
                }
                
                // Encode value as base64 if exists
                if (record.value() != null) {
                    message.put("value", Base64.getEncoder().encodeToString(record.value()));
                } else {
                    message.put("value", null);
                }
                
                message.put("partition", record.partition());
                message.put("offset", record.offset());
                
                results.add(message);
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
    public CompletableFuture<List<Map<String, Object>>> readAsync(String topic, int partition, long offset, int count, String clientRack) {
        try {
            List<Map<String, Object>> result = read(topic, partition, offset, count, clientRack);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
 * Get partition beginning and end offsets
 */
    @Async
    public CompletableFuture<Map<String, Long>> getOffsetsAsync(String topic, int partition, String clientRack) {
        KafkaConsumer<String, byte[]> consumer = null;
        try {
            consumer = connectionPool.borrowConsumer(clientRack);
            
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            
            // First, check if the topic exists and get partition count using consumer metadata
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
            if (partitionInfos == null || partition >= partitionInfos.size()) {
                // Invalid partition - return null to indicate non-existence
                return CompletableFuture.completedFuture(null);
            }
            
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


