FROM eclipse-temurin:17 as jre-build

RUN $JAVA_HOME/bin/jlink \
         --add-modules java.se \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM ubuntu:focal
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

WORKDIR /opt/googolplex-theater
COPY build/install/googolplex-theater/ .
EXPOSE 8000
EXPOSE 5353/udp
VOLUME ["/opt/googolplex-theater/conf"]
ENTRYPOINT ["./bin/googolplex-theater"]