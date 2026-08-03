# Two stages: a JDK image that builds the fat jar, and a JRE image that ships only the jar.
# No root pom.xml exists in CareerBridge -- all thirteen services are independent Maven projects,
# each with spring-boot-starter-parent as its parent and its own wrapper.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests are skipped here on purpose: they are run by `mvnw test` on the host against the real
# PostgreSQL, and re-running them inside the image build would need that reachable from a build
# container that is not on the compose network.
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

EXPOSE 8093

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
