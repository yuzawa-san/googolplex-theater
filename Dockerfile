FROM adoptopenjdk:11-jre-hotspot-focal
WORKDIR /opt/java-app
COPY build/install/googolplex-theater/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/java-app/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]