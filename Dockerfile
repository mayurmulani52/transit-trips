# Stage 1: build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q
COPY src src
RUN ./gradlew jar --no-daemon -q

# Stage 2: minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/transit-trips-1.0.0.jar app.jar
RUN addgroup -S trips && adduser -S trips -G trips
USER trips
ENTRYPOINT ["java", "-jar", "app.jar"]
