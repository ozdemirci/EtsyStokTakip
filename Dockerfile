FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache wget
COPY --from=build /workspace/app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
