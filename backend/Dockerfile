# Dockerfile for todo-backend
# Dev build - Uses pre-built artifacts + H2 in-memory database

# Stage 1: Build JRE (jlink)
FROM eclipse-temurin:21-jdk AS jre-builder

# Build minimal jlink with known working modules
# jdk.unsupported includes sun.misc.Unsafe needed by Scala runtime
RUN jlink \
    --add-modules java.sql,java.naming,java.logging,java.net.http,java.management,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output /jre-minimal

# Stage 2: Final image
FROM ubuntu:22.04

# Install full JRE as fallback
RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Use jlink JRE
COPY --from=jre-builder /jre-minimal /opt/java

# Copy pre-built application
COPY target/docker-stage/ /app

# Environment
ENV JAVA_HOME=/opt/java
ENV PATH=$JAVA_HOME/bin:$PATH

EXPOSE 8080

WORKDIR /app

# Run
CMD ["bin/oursky-todo-backend"]