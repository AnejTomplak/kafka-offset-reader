package com.example.kafkaoffsetreader;

import com.example.kafkaoffsetreader.config.ExternalConfigLoader;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class KafkaReaderService {

    @Value("${kafka.bootstrap.servers}")
    private String bootstrapServers;

    @Value("${kafka.client.rack:}")
    private String clientRack;

    @Autowired
    private ExternalConfigLoader configLoader;

    public List<String> read(String topic, int partition, long offset, int count) {
        return read(topic, partition, offset, count, null);
    }

    public List<String> read(String topic, int partition, long offset, int count, String runtimeClientRack) {
        List<String> results = new ArrayList<>();

        Properties props = new Properties();
        
        // Use external config if available, fallback to application.properties
        String effectiveBootstrapServers = configLoader.getProperty("kafka.bootstrap.servers", bootstrapServers);
        String configClientRack = configLoader.getProperty("kafka.client.rack", clientRack);
        
        props.put("bootstrap.servers", effectiveBootstrapServers);
        props.put("group.id", "offset-reader-" + UUID.randomUUID()); // Unique group per request
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        // Disable auto-commit and set offset strategy
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");

        // Rack-Aware Follower Fetching
        // Runtime parameter takes precedence over external config, then application.properties
        String effectiveRack = runtimeClientRack != null ? runtimeClientRack : 
                              (!configClientRack.isEmpty() ? configClientRack : null);
        
        if (effectiveRack != null && !effectiveRack.isEmpty()) {
            props.put("client.rack", effectiveRack);
            String source = runtimeClientRack != null ? " (runtime override)" : 
                           (!configClientRack.equals(clientRack) ? " (external config)" : " (app config)");
            System.out.printf("🎯 Rack-aware fetching enabled: client.rack = %s%s%n", 
                effectiveRack, source);
        } else {
            System.out.println("📍 Using default leader fetching (client.rack not set)");
        }

        // Connection timeouts and debugging enhancements
        props.put("connections.max.idle.ms", "540000");
        props.put("metadata.max.age.ms", "300000");

        // Optional tuning
        props.put("fetch.max.wait.ms", "100");
        props.put("fetch.min.bytes", "1");
        props.put("max.poll.records", "100");
        props.put("request.timeout.ms", "5000");
        props.put("session.timeout.ms", "10000");
        props.put("heartbeat.interval.ms", "3000");
        props.put("client.id", "rack-aware-reader-" + UUID.randomUUID());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(tp));
            consumer.seek(tp, offset);

            System.out.printf("➡ Reading from topic=%s partition=%d offset=%d (rack=%s, servers=%s)%n", 
                topic, partition, offset, 
                effectiveRack != null ? effectiveRack : "leader",
                effectiveBootstrapServers);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, String> record : records) {
                results.add(record.value());
                if (results.size() >= count) break;
            }

            System.out.printf("✅ Retrieved %d record(s)%n", results.size());

        } catch (Exception e) {
            System.err.println("❌ Error during Kafka read: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }
}
