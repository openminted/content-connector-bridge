FROM tomcat:9.0-alpine
MAINTAINER "kgiann78@gmail.com"

RUN ["rm", "-fr", "/usr/local/tomcat/webapps/"]
COPY ./target/content-connector-bridge.war /usr/local/tomcat/webapps/content-connector-bridge.war

CMD ["catalina.sh", "run"]