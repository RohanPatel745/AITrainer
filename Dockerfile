FROM openjdk:17-jdk-alpine

# Install necessary dependencies
RUN apk add --no-cache curl bash zip

# Install SDKMAN
RUN curl -s https://get.sdkman.io | bash

# Set environment variables for SDKMAN and Gradle
ENV SDKMAN_DIR=$HOME/.sdkman
ENV PATH=$SDKMAN_DIR/bin:$PATH

# Install Gradle using SDKMAN
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && sdk install gradle"

# Set working directory
WORKDIR /app

# Copy the project files with correct permissions
COPY . .

# Ensure gradlew has execute permissions
RUN chmod +x ./gradlew

# Build the JAR file using Gradle
RUN ./gradlew build

# Expose the port your application runs on
EXPOSE 9090

# Run the generated JAR file
ENTRYPOINT ["java", "-jar", "build/libs/test-0.0.1-SNAPSHOT.jar"]
