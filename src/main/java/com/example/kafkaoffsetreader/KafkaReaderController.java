package com.example.kafkaoffsetreader;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class KafkaReaderController {

    private final KafkaReaderService kafkaReaderService;

    public KafkaReaderController(KafkaReaderService kafkaReaderService) {
        this.kafkaReaderService = kafkaReaderService;
    }

    // Standard Kafka REST API compatible endpoint
    @GetMapping("/topics/{topic}/partitions/{partition}/messages")
    public List<String> readMessages(
        @PathVariable String topic,
        @PathVariable int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) String clientRack
    ) {
        return kafkaReaderService.read(topic, partition, offset, count, clientRack);
    }

    // Legacy endpoint for backward compatibility
    @GetMapping("/read")
    public List<String> readMessagesLegacy(
        @RequestParam String topic,
        @RequestParam int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) String clientRack
    ) {
        return kafkaReaderService.read(topic, partition, offset, count, clientRack);
    }
}

