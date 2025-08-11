# Kafka Rack-Aware Test Results

## Zone-A Test Results (clientRack=zone-a)

| Broker | Zone | Port | Initial Connections | Final Connections | Connection Delta | Expected Role |
|--------|------|------|--------------------|--------------------|------------------|---------------|
| Broker 1 | zone-a | 9092 | 2 | 44 | **42** ⭐ | PRIMARY TARGET |
| Broker 2 | zone-b | 9094 | 2 | 22 | 20 | Secondary |
| Broker 3 | zone-c | 9096 | 4 | 24 | 20 | Secondary |

**Result**: ✅ Zone-A broker received 51% of traffic (42/82 total connections)

## Zone-B Test Results (clientRack=zone-b)

| Broker | Zone | Port | Initial Connections | Final Connections | Connection Delta | Expected Role |
|--------|------|------|--------------------|--------------------|------------------|---------------|
| Broker 1 | zone-a | 9092 | - | - | 12 | Secondary |
| Broker 2 | zone-b | 9094 | - | - | **30** ⭐ | PRIMARY TARGET |
| Broker 3 | zone-c | 9096 | - | - | 12 | Secondary |

**Result**: ✅ Zone-B broker received 55% of traffic (30/54 total connections)

## Zone-C Test Results (clientRack=zone-c)

| Broker | Zone | Port | Initial Connections | Final Connections | Connection Delta | Expected Role |
|--------|------|------|--------------------|--------------------|------------------|---------------|
| Broker 1 | zone-a | 9092 | 0 | 16 | 16 | Secondary |
| Broker 2 | zone-b | 9094 | 0 | 22 | 22 | Secondary |
| Broker 3 | zone-c | 9096 | 0 | 52 | **52** ⭐ | PRIMARY TARGET |

**Result**: ✅ Zone-C broker received 58% of traffic (52/90 total connections)