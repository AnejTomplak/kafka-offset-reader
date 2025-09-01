package com.example.kafkaoffsetreader;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.example.kafkaoffsetreader.config.ExternalConfigLoader;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KafkaProducerService {

    private final Map<String, KafkaProducer<String, byte[]>> producerPool = new ConcurrentHashMap<>();
    
    @Autowired
    private ExternalConfigLoader configLoader;

    /**
     * Send messages to Kafka topic asynchronously
     */
    @Async
    public CompletableFuture<Map<String, Object>> sendAsync(String topic, Map<String, Object> request, String clientRack) {
        try {
            KafkaProducer<String, byte[]> producer = getProducer(clientRack);
            
            // Safe cast with validation
            Object recordsObj = request.get("records");
            if (!(recordsObj instanceof List)) {
                throw new IllegalArgumentException("'records' field must be a list");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> offsets = new ArrayList<>();
            
            for (Map<String, Object> record : records) {
                String key = (String) record.get("key");  // Optional
                Object valueObj = record.get("value");
                Integer partition = (Integer) record.get("partition");  // Optional
                
                byte[] valueBytes;
                if (valueObj instanceof String) {
                    valueBytes = ((String) valueObj).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    valueBytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(valueObj);
                }
                
                // Create ProducerRecord based on what's provided
                ProducerRecord<String, byte[]> producerRecord;
                if (partition != null && key != null) {
                    producerRecord = new ProducerRecord<>(topic, partition, key, valueBytes);
                } else if (partition != null) {
                    producerRecord = new ProducerRecord<>(topic, partition, null, valueBytes);
                } else if (key != null) {
                    producerRecord = new ProducerRecord<>(topic, key, valueBytes);
                } else {
                    producerRecord = new ProducerRecord<>(topic, valueBytes);
                }
                
                RecordMetadata metadata = producer.send(producerRecord).get();
                Map<String, Object> offsetInfo = new HashMap<>();
                offsetInfo.put("partition", metadata.partition());
                offsetInfo.put("offset", metadata.offset());
                offsets.add(offsetInfo);
            }
            
            response.put("offsets", offsets);
            return CompletableFuture.completedFuture(response);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to send messages to Kafka", e);
        }
    }

    private KafkaProducer<String, byte[]> getProducer(String rack) {
        String poolKey = rack != null ? rack : "default";
        return producerPool.computeIfAbsent(poolKey, k -> {
            Properties props = createProducerProperties(rack);
            return new KafkaProducer<>(props);
        });
    }

    private Properties createProducerProperties(String rack) {
        Properties props = new Properties();
        
        // Basic producer config
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
            configLoader.getProperty("kafka.bootstrap.servers", "localhost:9092"));
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        
        // Performance settings from ER config
        copyIfPresent(props, "kafka.acks", ProducerConfig.ACKS_CONFIG);
        copyIfPresent(props, "kafka.retries", ProducerConfig.RETRIES_CONFIG);
        copyIfPresent(props, "kafka.batch.size", ProducerConfig.BATCH_SIZE_CONFIG);
        copyIfPresent(props, "kafka.linger.ms", ProducerConfig.LINGER_MS_CONFIG);
        copyIfPresent(props, "kafka.buffer.memory", ProducerConfig.BUFFER_MEMORY_CONFIG);
        
        // Client identification and rack
        String clientIdPrefix = configLoader.getProperty("kafka.client.id", "er-kafka-rest-producer");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientIdPrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        
        if (rack != null && !"default".equalsIgnoreCase(rack)) {
            props.put("client.rack", rack);
        } else {
            String erRack = configLoader.getProperty("kafka.client.rack", null);
            if (erRack != null && !erRack.isEmpty()) {
                props.put("client.rack", erRack);
            }
        }
        
        return props;
    }

    private void copyIfPresent(Properties target, String erKey, String kafkaKey) {
        String value = configLoader.getProperty(erKey, null);
        if (value != null && !value.isEmpty()) {
            target.put(kafkaKey, value.split("#")[0].trim()); // Remove comments
        }
    }
}
