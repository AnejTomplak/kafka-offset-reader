package com.example.kafkaoffsetreader;

/* HOW TO RUN
 * 1. Ensure Kafka is running and accessible.
 * 2. Set the following environment variables:
 *    - KAFKA_BOOTSTRAP_SERVERS: Kafka broker address (e.g., localhost:9092)
 *    - CLIENT_RACK: Broker zone (optional, for rack-aware fetching)
 *    - EXTERNAL_CONFIG_PATH: Path to external properties file (optional)
 * 3. Run this application using:
 *    COMPILE: mvn clean compile
 *    RUN: mvn spring-boot:run
 * 4. Access the API at:
 *    GET /read?topic=your_topic&partition=0&offset=12345&count=10
 *    GET /read?topic=your_topic&partition=0&offset=12345&count=10&clientRack=zone-a
 *    GET /topics/{topic}/partitions/{partition}/messages  (main endpoint)
 *    GET /topics/{topic}/partitions/{partition}/messages?clientRack=zone-a  (rack-aware)
 *    GET /monitoring/pool-stats  (connection pool monitoring)
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync
public class KafkaOffsetReaderApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KafkaOffsetReaderApplication.class, args);
    }
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kafka-async-");
        executor.initialize();
        return executor;
    }
}
