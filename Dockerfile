FROM eclipse-temurin:17-jre-focal
WORKDIR /opt/googolplex-theater
COPY build/install/googolplex-theater/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/googolplex-theater/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]