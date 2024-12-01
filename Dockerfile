# Use the official OpenJDK image
FROM openjdk:17-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the entire project into the container
COPY . .

# Install Gradle (or Maven if you're using it)
RUN apk add --no-cache curl bash \
    && curl -s https://get.sdkman.io | bash \
    && source $HOME/.sdkman/bin/sdkman-init.sh \
    && sdk install gradle

# Build the JAR file using Gradle (or Maven if you're using it)
RUN ./gradlew build

# Expose the port your application runs on
EXPOSE 9090

# Run the generated JAR file
ENTRYPOINT ["java", "-jar", "build/libs/test-0.0.1-SNAPSHOT.jar"]
