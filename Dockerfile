FROM circleci/openjdk:11-jdk-stretch AS BUILD_STAGE
USER root
WORKDIR /app
RUN mkdir -p src/main/java
COPY build.gradle gradlew ./
COPY gradle gradle
RUN ./gradlew --version
COPY . .
RUN ./gradlew build installDist

FROM openjdk:11-jre-slim
WORKDIR /opt/java-app
COPY --from=BUILD_STAGE /app/build/install/googolplex-theater/ .
EXPOSE 8080
ENTRYPOINT ["./bin/googolplex-theater"]