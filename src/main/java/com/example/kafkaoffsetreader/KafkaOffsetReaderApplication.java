package com.example.kafkaoffsetreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@SpringBootApplication
public class KafkaOffsetReaderApplication {

    public static void main(String[] args) {
        String configFile = null;
        
        // Check if config file argument is provided
        if (args.length > 0) {
            configFile = args[0];
            System.out.println("Using specified configuration file: " + configFile);
        } else {
            // Try to find default config file in current directory
            String[] defaultPaths = {
                "./etc/kafka-rest/er-kafka-rest.properties",
                "etc/kafka-rest/er-kafka-rest.properties",
                "./er-kafka-rest.properties",
                "er-kafka-rest.properties"
            };
            
            for (String defaultPath : defaultPaths) {
                Path path = Paths.get(defaultPath);
                if (Files.exists(path) && Files.isReadable(path)) {
                    configFile = defaultPath;
                    System.out.println("Found and using default configuration file: " + configFile);
                    break;
                }
            }
            
            if (configFile == null) {
                System.out.println("No configuration file specified and no default config found, using embedded application.properties");
            }
        }
        
        // Load external configuration if found
        if (configFile != null) {
            try {
                loadExternalConfig(configFile);
            } catch (IOException e) {
                System.err.println("Failed to load configuration file: " + configFile + " - " + e.getMessage());
                System.err.println("Falling back to embedded application.properties");
            }
        }
        
        SpringApplication.run(KafkaOffsetReaderApplication.class, args);
    }
    
    private static void loadExternalConfig(String configFile) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            System.out.println("Successfully loaded " + props.size() + " properties from: " + configFile);
            
            // Set properties as system properties so they can be used by Spring Boot
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                System.setProperty(key, value);
                // Debug output for key properties
                if (key.contains("bootstrap.servers") || key.contains("client.rack") || 
                    key.contains("fetch.max.wait") || key.contains("server.port")) {
                    System.out.println("  " + key + " = " + value);
                }
            }
        }
    }
}