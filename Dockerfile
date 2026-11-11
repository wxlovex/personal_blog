FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src/ ./src/
COPY database/ ./database/
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/
RUN mvn -B -ntp verify

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/target/personal_blog-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=15.0 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
