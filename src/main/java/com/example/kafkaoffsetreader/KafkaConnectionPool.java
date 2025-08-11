package com.example.kafkaoffsetreader;

import com.example.kafkaoffsetreader.config.ExternalConfigLoader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Kafka Connection Pool for High-Performance Request Handling
 * 
 * Maintains a pool of reusable KafkaConsumer instances to handle
 * high-volume concurrent requests efficiently.
 * 
 * Features:
 * - Connection pooling to avoid TCP overhead
 * - Thread-safe consumer management
 * - Rack-aware consumer separation
 * - Automatic connection cleanup
 */
@Component
public class KafkaConnectionPool {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConnectionPool.class);
    
    @Value("${kafka.bootstrap.servers}")
    private String bootstrapServers;
    
    @Value("${kafka.client.rack:}")
    private String defaultClientRack;
    
    @Autowired
    private ExternalConfigLoader configLoader;
    
    // Pool configuration
    private static final int MAX_POOL_SIZE = 50;
    private static final int MIN_POOL_SIZE = 15;
    private static final long CONSUMER_TIMEOUT_MS = 30000; // 30 seconds
    
    // Separate pools for different rack configurations
    private final Map<String, BlockingQueue<KafkaConsumer<String, String>>> consumerPools = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> poolLocks = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializePools() {
        logger.info("🏊 Initializing Kafka connection pools...");
        
        // Initialize pools for common rack configurations
        String[] commonRacks = {"zone-a", "zone-b", "zone-c", "default"};
        
        for (String rack : commonRacks) {
            createPool(rack);
        }
        
        logger.info("✅ Kafka connection pools initialized with {} pools", consumerPools.size());
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("🧹 Cleaning up Kafka connection pools...");
        
        consumerPools.forEach((rack, pool) -> {
            while (!pool.isEmpty()) {
                try {
                    KafkaConsumer<String, String> consumer = pool.poll();
                    if (consumer != null) {
                        consumer.close();
                    }
                } catch (Exception e) {
                    logger.warn("Error closing consumer: {}", e.getMessage());
                }
            }
        });
        
        consumerPools.clear();
        poolLocks.clear();
        
        logger.info("✅ Kafka connection pools cleaned up");
    }
    
    /**
     * Get a consumer from the pool for the specified rack
     */
    public KafkaConsumer<String, String> borrowConsumer(String clientRack) {
        String effectiveRack = clientRack != null ? clientRack : getEffectiveRack();
        
        BlockingQueue<KafkaConsumer<String, String>> pool = consumerPools.get(effectiveRack);
        if (pool == null) {
            // Create pool on-demand
            createPool(effectiveRack);
            pool = consumerPools.get(effectiveRack);
        }
        
        KafkaConsumer<String, String> consumer = pool.poll();
        if (consumer == null) {
            // Pool is empty, create new consumer
            consumer = createConsumer(effectiveRack);
            logger.debug("📈 Created new consumer for rack: {} (pool was empty)", effectiveRack);
        } else {
            logger.debug("♻️ Reused consumer from pool for rack: {}", effectiveRack);
        }
        
        return consumer;
    }
    
    /**
     * Return a consumer to the pool
     */
    public void returnConsumer(KafkaConsumer<String, String> consumer, String clientRack) {
        String effectiveRack = clientRack != null ? clientRack : getEffectiveRack();
        
        BlockingQueue<KafkaConsumer<String, String>> pool = consumerPools.get(effectiveRack);
        if (pool != null && pool.size() < MAX_POOL_SIZE) {
            pool.offer(consumer);
            logger.debug("🔄 Returned consumer to pool for rack: {}", effectiveRack);
        } else {
            // Pool is full or doesn't exist, close the consumer
            consumer.close();
            logger.debug("🗑️ Closed excess consumer for rack: {}", effectiveRack);
        }
    }
    
    /**
     * Create a new pool for the specified rack
     */
    private void createPool(String rack) {
        consumerPools.computeIfAbsent(rack, r -> {
            logger.info("🆕 Creating new consumer pool for rack: {}", r);
            BlockingQueue<KafkaConsumer<String, String>> pool = new LinkedBlockingQueue<>();
            
            // Pre-populate with minimum consumers
            for (int i = 0; i < MIN_POOL_SIZE; i++) {
                pool.offer(createConsumer(r));
            }
            
            poolLocks.put(r, new ReentrantLock());
            logger.info("✅ Created consumer pool for rack {} with {} pre-allocated consumers", r, MIN_POOL_SIZE);
            return pool;
        });
    }
    
    /**
     * Create a new KafkaConsumer instance
     */
    private KafkaConsumer<String, String> createConsumer(String clientRack) {
        Properties props = new Properties();
        
        // Use external config if available
        String effectiveBootstrapServers = configLoader.getProperty("kafka.bootstrap.servers", bootstrapServers);
        
        props.put("bootstrap.servers", effectiveBootstrapServers);
        props.put("group.id", "pooled-consumer-group-" + clientRack);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");
        
        // Rack-aware configuration
        if (clientRack != null && !clientRack.equals("default")) {
            props.put("client.rack", clientRack);
        }
        
        // Performance tuning for high-throughput
        props.put("fetch.max.wait.ms", "500");  // Balanced: wait up to 500ms for batches
        props.put("fetch.min.bytes", "1");
        props.put("max.poll.records", "500");
        props.put("request.timeout.ms", "10000");
        props.put("session.timeout.ms", "20000");
        props.put("heartbeat.interval.ms", "5000");
        props.put("connections.max.idle.ms", "300000");
        props.put("metadata.max.age.ms", "180000");
        props.put("client.id", "pooled-consumer-" + clientRack + "-" + UUID.randomUUID());
        
        logger.debug("🔧 Created new KafkaConsumer for rack: {}", clientRack);
        return new KafkaConsumer<>(props);
    }
    
    /**
     * Get effective rack from configuration hierarchy
     */
    private String getEffectiveRack() {
        String configClientRack = configLoader.getProperty("kafka.client.rack", defaultClientRack);
        return configClientRack.isEmpty() ? "default" : configClientRack;
    }
    
    /**
     * Get pool statistics for monitoring
     */
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        
        consumerPools.forEach((rack, pool) -> {
            Map<String, Object> poolStats = new HashMap<>();
            poolStats.put("available", pool.size());
            poolStats.put("maxSize", MAX_POOL_SIZE);
            poolStats.put("minSize", MIN_POOL_SIZE);
            stats.put(rack, poolStats);
        });
        
        return stats;
    }
}
