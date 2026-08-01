# Two stages: a JDK image that builds the fat jar, and a JRE image that ships only the jar.
# No root pom.xml exists in CareerBridge -- all nine services are independent Maven projects, each
# with spring-boot-starter-parent as its parent and its own wrapper. So the build context is this
# service directory alone and there is nothing to resolve from a sibling module.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Wrapper and pom first, src last: this is the whole point of the split. Editing a .java file then
# re-runs only the package step, while the dependency download layer stays cached.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# The wrapper is maven-wrapper.properties only (distributionType=only-script), so ./mvnw downloads
# Apache Maven itself on first use -- via busybox wget and unzip, both present in this image.
# The sed strips CR characters: mvnw is generated on a Windows checkout, and a trailing CR on the
# shebang line makes /bin/sh report "no such file or directory" for the interpreter rather than for
# the script, which is a genuinely baffling error to debug. chmod because the execute bit does not
# survive a Windows filesystem.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests are skipped here on purpose: they are run by `mvnw test` on the host against the real
# PostgreSQL/RabbitMQ, and re-running them inside the image build would need those same services
# reachable from a build container that is not on the compose network.
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root. A JVM serving HTTP has no reason to run as uid 0.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# *.jar matches the repackaged fat jar only -- spring-boot-maven-plugin leaves the original
# alongside it as *.jar.original, which this glob does not pick up.
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

EXPOSE 8089

# UseContainerSupport is on by default since JDK 10 but is stated explicitly so it survives anyone
# adding a -XX:-UseContainerSupport-style flag later. MaxRAMPercentage matters more: without it the
# JVM takes a quarter of the container limit and spends the rest on nothing.
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
