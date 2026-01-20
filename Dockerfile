FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

COPY lib ./lib

RUN mvn -q org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file \
    -Dfile=lib/kafka-clients-4.2.0-follower-fetch.jar \
    -DgroupId=org.apache.kafka \
    -DartifactId=kafka-clients \
    -Dversion=4.2.0-follower-fetch \
    -Dpackaging=jar

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
