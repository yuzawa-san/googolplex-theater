FROM eclipse-temurin:20-jre-jammy
WORKDIR /opt/googolplex-theater
COPY build/install/googolplex-theater-boot/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/googolplex-theater/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]