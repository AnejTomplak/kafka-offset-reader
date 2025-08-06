package com.example.kafkaoffsetreader;

/* HOW TO RUN
 * 1. Ensure Kafka is running and accessible.
 * 2. Set the following environment variables:
 *    - KAFKA_BOOTSTRAP_SERVERS: Kafka broker address (e.g., localhost:9092)
 *    - KAFKA_PREFERRED_READ_REPLICA: Broker ID to prefer for reading (optional, -1 for leader)
 * 3. Run this application using:
 *    COMPILE: mvn clean compile
 *    RUN: mvn spring-boot:run
 * 4. Access the API at:
 *    GET /read?topic=your_topic&partition=0&offset=12345&count=10
 *    GET /read?topic=your_topic&partition=0&offset=12345&count=10&preferredReplica=1
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaOffsetReaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(KafkaOffsetReaderApplication.class, args);
    }
}
