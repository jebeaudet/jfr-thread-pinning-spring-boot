# https://hub.docker.com/r/azul/zulu-openjdk-alpine/tags?page=1&name=21-jre-headless
FROM azul/zulu-openjdk-alpine:21-latest@sha256:18112d28f7742d9f7e544884e5d5df32112a3c95af00d1c32c8af4eb1d0db355

ARG JAVA_VERSION=21

# Assert the java version is the expected one
RUN [[ $(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1) -eq ${JAVA_VERSION} ]] && exit 0 || (echo -e "*********************\nThe base image has the wrong java version!!\n************************" && exit 1)

RUN mkdir /app

COPY target/jfr-thread-pinning-spring-boot-0.0.1-SNAPSHOT.jar /app/jfr.jar

CMD ["java", "-Xms100M", "-Xmx5000M", "-XX:+AlwaysPreTouch", "-XX:NativeMemoryTracking=detail", "-Xlog:gc*:gc.log::filecount=0", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/app/oom.hprof", "-jar", "/app/jfr.jar"]