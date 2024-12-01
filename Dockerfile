# Stage 1: Build the JAR file
FROM maven:3.8-openjdk-17 AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml and src folder (exclude JAR from Git)
COPY pom.xml .
COPY src ./src

# Run Maven to build the JAR file
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime environment
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/your-app.jar ./app.jar

# Expose the necessary port (if required by your app)
EXPOSE 9090

# Command to run the JAR file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]