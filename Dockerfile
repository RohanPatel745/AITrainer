# Use a Gradle image with JDK
FROM gradle:7.6-jdk17 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the Gradle files to the container
COPY build.gradle settings.gradle ./

# Download dependencies
RUN gradle build --no-daemon

# Copy the rest of your project
COPY . .

# Build the JAR file
RUN gradle jar --no-daemon

# Use a smaller JRE image to run the application
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the port your website runs on
EXPOSE 8080

# Command to run the JAR file
CMD ["java", "-jar", "app.jar"]