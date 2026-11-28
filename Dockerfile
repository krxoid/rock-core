FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew build --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/rock-core.jar

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-Dorg.jline.utils.Log.level=DEBUG", "-jar", "/app/rock-core.jar"]