FROM openjdk:17-jdk-alpine

# Install necessary dependencies
RUN apk add --no-cache curl bash zip

# Install SDKMAN and Gradle
RUN curl -s https://get.sdkman.io | bash \
    && source $HOME/.sdkman/bin/sdkman-init.sh \
    && sdk install gradle

# Set working directory
WORKDIR /app

# Copy the project files
COPY . .

# Set environment variable for Gradle
ENV GRADLE_HOME=$HOME/.sdkman/candidates/gradle/current
ENV PATH=$GRADLE_HOME/bin:$PATH

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
