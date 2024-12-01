# Use the official OpenJDK image
FROM openjdk:17-jdk-alpine

# Set working directory inside the container
WORKDIR /app

# Copy the packaged JAR file into the container
COPY build/libs/test-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your application runs on
EXPOSE 9090

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]
