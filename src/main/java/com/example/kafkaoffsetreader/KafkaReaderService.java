package com.example.kafkaoffsetreader;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class KafkaReaderService {

    @Value("${kafka.bootstrap.servers}")
    private String bootstrapServers;

    @Value("${kafka.preferred.read.replica:-1}")
    private int preferredReadReplica;

    public List<String> read(String topic, int partition, long offset, int count) {
        return read(topic, partition, offset, count, null);
    }

    public List<String> read(String topic, int partition, long offset, int count, Integer runtimePreferredReplica) {
        List<String> results = new ArrayList<>();

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "offset-reader-" + UUID.randomUUID()); // Unique group per request
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        // Disable auto-commit and set offset strategy
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");

        // Follower Fetching - Direct replica reading capability
        // Runtime parameter takes precedence over configuration
        Integer effectiveReplica = runtimePreferredReplica != null ? runtimePreferredReplica : 
                                   (preferredReadReplica >= 0 ? preferredReadReplica : null);
        
        if (effectiveReplica != null && effectiveReplica >= 0) {
            props.put("preferred.read.replica", effectiveReplica.toString());
            System.out.printf("Follower fetching enabled: preferred replica = %d%s%n", 
                effectiveReplica, 
                runtimePreferredReplica != null ? " (runtime override)" : " (config)");
        } else {
            System.out.println("Using default leader fetching (preferred.read.replica not set)");
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
        props.put("client.id", "follower-reader-" + UUID.randomUUID());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(tp));
            consumer.seek(tp, offset);

            System.out.printf("➡ Reading from topic=%s partition=%d offset=%d, preferred_replica=%s)%n", 
                topic, partition, offset, 
                effectiveReplica != null ? effectiveReplica.toString() : "leader");

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, String> record : records) {
                results.add(record.value());
                if (results.size() >= count) break;
            }

            System.out.printf("Retrieved %d record(s)%n", results.size());

        } catch (Exception e) {
            System.err.println("Error during Kafka read: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }
}
