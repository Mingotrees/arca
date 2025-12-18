FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests


# ================================
# 2️⃣ Runtime stage (JRE only)
# ================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Cloud Run listens on 8080
EXPOSE 8080

# Copy built jar
COPY --from=builder /build/target/*.jar app.jar

# Optional but recommended JVM flags for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Spring profile (override in Cloud Run if needed)
ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]