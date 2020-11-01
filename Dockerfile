FROM openjdk:11-jdk AS BUILD_STAGE
USER root
WORKDIR /app
RUN mkdir -p src/main/java
COPY build.gradle gradlew ./
COPY gradle gradle
RUN ./gradlew --version
COPY . .
RUN ./gradlew installDist

FROM openjdk:11-jre-slim
WORKDIR /opt/java-app
COPY --from=BUILD_STAGE /app/build/install/googolplex-theater/ .
ENV JAVA_OPTS=""
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/java-app/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]