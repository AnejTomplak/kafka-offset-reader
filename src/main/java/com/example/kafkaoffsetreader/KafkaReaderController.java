package com.example.kafkaoffsetreader;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/read")
public class KafkaReaderController {

    private final KafkaReaderService kafkaReaderService;

    public KafkaReaderController(KafkaReaderService kafkaReaderService) {
        this.kafkaReaderService = kafkaReaderService;
    }

    @GetMapping
    public List<String> readMessages(
        @RequestParam String topic,
        @RequestParam int partition,
        @RequestParam long offset,
        @RequestParam(defaultValue = "1") int count,
        @RequestParam(required = false) Integer preferredReplica
    ) {
        return kafkaReaderService.read(topic, partition, offset, count, preferredReplica);
    }
}

