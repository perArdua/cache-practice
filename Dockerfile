# build
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# gradle cache
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# copy src
COPY src /app/src

# test
RUN ./gradlew bootJar -x test

# using image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# copy jar
COPY --from=builder /app/build/libs/*SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
