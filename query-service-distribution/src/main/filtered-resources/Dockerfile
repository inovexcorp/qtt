FROM openjdk:17.0.2-jdk

# Maven filtered to produce correct versioning.
ENV VERSION ${project.version}
ENV QTT_HOME /opt/qtt
ENV KARAF_LOG_CONSOLE INFO
ENV KARAF_INSTALL_PATH /opt
ENV KARAF_HOME $KARAF_INSTALL_PATH/qtt
ENV KARAF_EXEC exec
ENV PATH $PATH:$KARAF_HOME/bin
EXPOSE 8443
EXPOSE 8888
RUN mkdir -p /opt/qtt/
# drop the distribution files into the docker image.
COPY ./run.sh /opt/qtt/bin/run.sh
RUN chmod +x /opt/qtt/bin/run.sh
COPY ./bin/ /opt/qtt/bin/
COPY ./data/ /opt/qtt/data/
COPY ./deploy/ /opt/qtt/deploy/
COPY ./etc/ /opt/qtt/etc/
COPY ./lib/ /opt/qtt/lib/
COPY ./system/ /opt/qtt/system/
# Set the working directory to our qtt home
WORKDIR /opt/qtt
CMD /opt/qtt/bin/run.sh
