FROM openjdk:17-jdk-slim

LABEL maintainer="Maple Payments Team"
LABEL version="1.0.0"
LABEL description="Maple Payments Hub - Corporate Payment Initiation System"

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create application user
RUN groupadd -r maple && useradd -r -g maple maple

# Set working directory
WORKDIR /app

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source code
COPY src src

# Build application
RUN ./gradlew build --no-daemon -x test

# Create directories for data and logs
RUN mkdir -p /app/data /app/logs && chown -R maple:maple /app

# Switch to application user
USER maple

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "/app/build/libs/maple-payments-hub.jar"]
