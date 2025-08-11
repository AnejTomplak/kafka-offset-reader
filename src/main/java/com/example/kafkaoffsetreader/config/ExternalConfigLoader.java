package com.example.kafkaoffsetreader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * External Configuration Loader
 * 
 * Loads configuration from an external properties file located relative to the JAR file.
 * The configuration file is expected at: <jar-location>/etc/kafka-rest/er-kafka-rest.properties
 * 
 * Example usage:
 * - JAR located at: /opt/kafka-offset-reader/kafka-offset-reader.jar
 * - Config file at: /opt/kafka-offset-reader/etc/kafka-rest/er-kafka-rest.properties
 */
@Component
public class ExternalConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalConfigLoader.class);
    
    private Properties externalProperties;
    
    public ExternalConfigLoader() {
        this.externalProperties = loadExternalConfig();
    }
    
    /**
     * Load configuration from external properties file
     * @return Properties object with external configuration, or empty if file not found
     */
    private Properties loadExternalConfig() {
        Properties props = new Properties();
        
        try {
            // Get the location of the current JAR file
            String jarPath = getJarLocation();
            logger.info("JAR location detected: {}", jarPath);
            
            // Try multiple locations for external config
            File configFile = findConfigFile(jarPath);
            
            if (configFile != null && configFile.exists() && configFile.isFile()) {
                logger.info("✅ External configuration file found: {}", configFile.getAbsolutePath());
                
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                    logger.info("✅ Successfully loaded {} properties from external config", props.size());
                    
                    // Log loaded properties (excluding sensitive data)
                    props.forEach((key, value) -> {
                        String keyStr = key.toString();
                        if (keyStr.toLowerCase().contains("password") || keyStr.toLowerCase().contains("secret")) {
                            logger.info("  {}: [REDACTED]", keyStr);
                        } else {
                            logger.info("  {}: {}", keyStr, value);
                        }
                    });
                } catch (IOException e) {
                    logger.error("❌ Failed to read external config file: {}", e.getMessage());
                }
            } else {
                logger.warn("⚠️ External configuration file not found in any expected location");
                logger.info("   Using default configuration from application.properties");
            }
            
        } catch (Exception e) {
            logger.error("❌ Error loading external configuration: {}", e.getMessage(), e);
        }
        
        return props;
    }
    
    /**
     * Find external configuration file in multiple possible locations
     * @param jarPath The detected JAR/classes location
     * @return File object pointing to the config file, or null if not found
     */
    private File findConfigFile(String jarPath) {
        String[] searchPaths = {
            // 1. Relative to JAR/classes directory (production)
            Paths.get(jarPath).getParent().resolve("etc/kafka-rest/er-kafka-rest.properties").toString(),
            // 2. Project root (development - when running from target/classes)
            Paths.get(jarPath).getParent().getParent().resolve("etc/kafka-rest/er-kafka-rest.properties").toString(),
            // 3. Current working directory
            Paths.get("etc/kafka-rest/er-kafka-rest.properties").toString()
        };
        
        for (String path : searchPaths) {
            File configFile = new File(path);
            logger.info("Checking for external config at: {}", configFile.getAbsolutePath());
            if (configFile.exists() && configFile.isFile()) {
                logger.info("✅ Found external config at: {}", configFile.getAbsolutePath());
                return configFile;
            }
        }
        
        return null;
    }
    
    /**
     * Get the location of the current JAR file
     * @return String path to the JAR file directory
     */
    private String getJarLocation() throws URISyntaxException {
        // Get the location of the class file
        String jarPath = ExternalConfigLoader.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
        
        // Handle Windows paths (remove leading slash)
        if (System.getProperty("os.name").toLowerCase().startsWith("windows") && jarPath.startsWith("/")) {
            jarPath = jarPath.substring(1);
        }
        
        return jarPath;
    }
    
    /**
     * Get a property value from external configuration
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getProperty(String key) {
        return externalProperties.getProperty(key);
    }
    
    /**
     * Get a property value with default fallback
     * @param key Property key
     * @param defaultValue Default value if property not found
     * @return Property value or default value
     */
    public String getProperty(String key, String defaultValue) {
        return externalProperties.getProperty(key, defaultValue);
    }
    
    /**
     * Check if external configuration was loaded successfully
     * @return true if external config file was found and loaded
     */
    public boolean hasExternalConfig() {
        return !externalProperties.isEmpty();
    }
    
    /**
     * Get all external properties
     * @return Properties object with all external configuration
     */
    public Properties getAllProperties() {
        return new Properties(externalProperties);
    }
}
