FROM eclipse-temurin:25-jre-alpine
WORKDIR /opt/googolplex-theater
COPY build/install/googolplex-theater-boot/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/googolplex-theater/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]