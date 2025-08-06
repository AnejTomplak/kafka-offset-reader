# Kafka Follower Fetching Microservice

A Spring Boot microservice that demonstrates **rack-aware follower fetching** functionality in Apache Kafka using a custom-patched Kafka client. This application enables direct consumption from specific Kafka replicas rather than being restricted to partition leaders.

## 🎯 Purpose

This project solves a critical limitation in standard Kafka clients: **the inability to read from follower replicas**. By implementing a custom patch with the `preferred.read.replica` property, this microservice can:

- **Reduce cross-zone network traffic** by reading from local replicas
- **Improve latency** by avoiding leader-only consumption bottlenecks  
- **Enable rack-aware consumption patterns** for geographically distributed deployments
- **Demonstrate advanced Kafka client customization** for enterprise use cases

## 🚀 Key Features

- ✅ **Direct follower replica consumption** via `preferred.read.replica` property
- ✅ **Runtime replica override** through REST API parameters
- ✅ **Rack-aware broker configuration** with 3-broker test cluster
- ✅ **Network traffic validation** to prove follower fetching works
- ✅ **Spring Boot REST API** for easy testing and integration
- ✅ **Comprehensive logging** for debugging and verification


## 📋 Prerequisites

- **Java 11+**
- **Maven 3.6+**
- **Docker & Docker Compose** (for Kafka cluster)
- **Windows/Linux/macOS** (tested on Windows PowerShell)

## 🔧 Installation & Setup

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd kafka-offset-reader
```

### 2. Start Kafka Cluster

```bash
# Navigate to Docker Compose directory
cd ../kafka-stack-docker-compose

# Start the 3-broker Kafka cluster
docker-compose up -d

# Verify all services are running
docker-compose ps
```

### 3. Build the Application

```bash
# Return to application directory
cd ../kafka-offset-reader

# Clean build with automatic patched JAR installation
mvn clean compile

# The maven-install-plugin will automatically install the patched Kafka client
```

### 4. Create Test Topic

```bash
# Create a topic with 3 partitions and 3 replicas
docker exec kafka-broker-1 kafka-topics --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server kafka-broker-1:9092
```

### 5. Produce Test Data

```bash
# Produce some test messages
docker exec -i kafka-broker-1 kafka-console-producer \
  --topic test-topic \
  --bootstrap-server kafka-broker-1:9092 << EOF
{"id": 1, "message": "Hello from producer 1"}
{"id": 2, "message": "Hello from producer 2"}  
{"id": 3, "message": "Hello from producer 3"}
EOF
```

## 🚀 Running the Application

### Start the Microservice

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080` with follower fetching enabled for broker ID 3.

## 📡 API Endpoints

### Endpoint

```http
GET /read
```

### Examples

**Read Messages (Default Configuration)**
```bash
curl "http://localhost:8080/read?topic=test-topic&partition=0&offset=0&count=3"
```
*Default behavior: Reads from broker ID 3 (configured in `application.properties`)*

**Read with Runtime Replica Override**
```bash
curl "http://localhost:8080/read?topic=test-topic&partition=0&offset=0&count=3&preferredReplica=2"
```
*Override behavior: Forces reading from broker ID 2, ignoring default configuration*

```

### API Parameters

- **`topic`** (required): Kafka topic name
- **`partition`** (required): Partition number to read from
- **`offset`** (required): Starting offset position
- **`count`** (optional, default=1): Number of messages to read
- **`preferredReplica`** (optional): Override the configured preferred replica
  - `-1`: Use leader (default Kafka behavior)
  - `1`: Use broker 1 (zone-a)
  - `2`: Use broker 2 (zone-b)
  - `3`: Use broker 3 (zone-c)

## 🔍 Verification & Testing

### 1. Check Partition Leadership

```bash
# View topic details and replica assignments
docker exec kafka-broker-1 kafka-topics --describe \
  --topic test-topic \
  --bootstrap-server kafka-broker-1:9092
```

### 2. Monitor Network Traffic (Advanced)

```bash
# Monitor traffic to specific broker (requires netstat/tcpdump)
netstat -an | findstr :9096    # Windows
netstat -an | grep :9096       # Linux/macOS
```

### 3. Application Logs

The application provides detailed logging showing which broker is being used:

```
2025-08-06 12:15:30 INFO  - Reading from topic: test-topic, partition: 0, replica: 3
2025-08-06 12:15:30 DEBUG - Using patched Kafka client with preferred.read.replica=3
2025-08-06 12:15:30 INFO  - Successfully read 1 messages from broker 3
```

## ⚙️ Configuration

### Application Properties

```properties
# Server Configuration  
server.port=8080

# Kafka Cluster Connection
kafka.bootstrap.servers=localhost:9092, localhost:9094, localhost:9096
kafka.client.dns.lookup=use_all_dns_ips

# Follower Fetching Configuration
# Set to broker ID (1, 2, or 3) for follower fetching
# Set to -1 for standard leader-only consumption
kafka.preferred.read.replica=3

# Enable debug logging
logging.level.org.apache.kafka.clients.consumer=DEBUG
```

### Runtime Override

You can override the replica selection per request:

```bash
# Read from broker 1
curl "http://localhost:8080/read?topic=test-topic&partition=0&offset=0&count=3&preferredReplica=1"

# Read from broker 2  
curl "http://localhost:8080/read?topic=test-topic&partition=0&offset=0&count=3&preferredReplica=2"

# Use default configuration (broker 3)
curl "http://localhost:8080/read?topic=test-topic&partition=0&offset=0&count=3"
```

## 🛠️ Technical Implementation

### Custom Kafka Client Patch

This project uses a **patched Kafka client** (`kafka-clients-3.7.1-follower-fetch.jar`) that adds:

- **`preferred.read.replica` property**: Specifies which broker ID to read from
- **Runtime override capability**: Allows changing replica selection per consumer
- **Enhanced logging**: Shows which broker is actually serving the requests

### Maven Configuration

The `pom.xml` includes an automatic installation plugin:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-install-plugin</artifactId>
  <executions>
    <execution>
      <id>install-patched-kafka</id>
      <phase>validate</phase>
      <goals>
        <goal>install-file</goal>
      </goals>
      <configuration>
        <file>lib/kafka-clients-3.7.1-follower-fetch.jar</file>
        <groupId>org.apache.kafka</groupId>
        <artifactId>kafka-clients</artifactId>
        <version>3.7.1-follower-fetch</version>
        <packaging>jar</packaging>
      </configuration>
    </execution>
  </executions>
</plugin>
```

This ensures the patched JAR is automatically installed to your local Maven repository during the build process.

## 📁 Project Structure

```
kafka-offset-reader/
├── lib/
│   ├── kafka-clients-3.7.1-follower-fetch.jar  # Patched Kafka client
├── src/main/java/com/example/kafkaoffsetreader/
│   ├── KafkaOffsetReaderApplication.java        # Spring Boot main class
│   ├── KafkaReaderController.java               # REST API endpoints
│   └── KafkaReaderService.java                  # Kafka consumption logic
├── src/main/resources/
│   └── application.properties                   # Configuration
├── pom.xml                                      # Maven configuration
└── README.md                                    # This file
```

## 🐛 Troubleshooting

### Common Issues

1. **"Could not resolve dependencies"**
   ```bash
   # Manually install the patched JAR
   mvn install:install-file -Dfile=lib/kafka-clients-3.7.1-follower-fetch.jar \
     -DgroupId=org.apache.kafka -DartifactId=kafka-clients \
     -Dversion=3.7.1-follower-fetch -Dpackaging=jar
   ```

2. **Connection refused to Kafka brokers**
   ```bash
   # Check if Docker containers are running
   docker-compose ps
   
   # Restart if needed
   docker-compose down && docker-compose up -d
   ```

3. **No messages returned**
   ```bash
   # Verify topic has data
   docker exec kafka-broker-1 kafka-console-consumer \
     --topic test-topic --from-beginning \
     --bootstrap-server kafka-broker-1:9092
   ```

### Debug Mode

Enable detailed logging by setting:

```properties
logging.level.org.apache.kafka.clients=DEBUG
logging.level.com.example.kafkaoffsetreader=DEBUG
```

## 🌟 Use Cases

### Enterprise Scenarios

1. **Multi-Region Deployments**: Read from local replicas to reduce inter-region bandwidth costs
2. **Load Distribution**: Spread read traffic across multiple brokers instead of overloading leaders
3. **Disaster Recovery**: Maintain read capability even when primary leaders are unavailable
4. **Compliance**: Meet data locality requirements by ensuring data stays within specific zones

### Performance Benefits

- **Reduced Network Latency**: Local replica reads eliminate cross-zone hops
- **Lower Leader Load**: Distributes read traffic away from partition leaders
- **Better Resource Utilization**: Leverages follower broker CPU and memory capacity
