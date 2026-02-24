FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY backend/gradlew ./gradlew
COPY backend/gradlew.bat ./gradlew.bat
COPY backend/build.gradle ./build.gradle
COPY backend/settings.gradle ./settings.gradle
COPY backend/gradle ./gradle
COPY backend/src ./src

RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Seoul

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
