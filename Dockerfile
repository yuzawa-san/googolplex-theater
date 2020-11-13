FROM adoptopenjdk:11-jdk-hotspot-focal AS BUILD_STAGE
USER root
RUN apt-get update && apt-get install -y \
  git \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir -p src/main/java
COPY build.gradle gradlew ./
COPY gradle gradle
RUN ./gradlew --version
COPY . .
RUN ./gradlew installDist

FROM adoptopenjdk:11-jre-hotspot-focal
WORKDIR /opt/java-app
COPY --from=BUILD_STAGE /app/build/install/googolplex-theater/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/java-app/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]