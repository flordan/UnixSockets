# UnixSockets
Library for Communicating trhough UnixScokets and NIO


# Compilation
docker run -it --rm -v "$PWD":/home/gradle/project -w /home/gradle/project gradle:8.5.0-jdk17-jammy gradle clean build

# Run Examples

    mkdir -p /tmp/sockets

## Echo service

### On baremetal java:

Server

    java -classpath app/build/libs/app.jar EchoServer /tmp/sockets/path.sock

Client

    java -classpath app/build/libs/app.jar EchoClient /tmp/sockets/path.sock


### Using Containers:

Server

    docker run --rm -it -v /tmp/sockets/:/var/run/sockets -v ${PWD}/app/build/libs/app.jar:/app/app.jar openjdk:19-jdk-oracle java -classpath /app/app.jar EchoServer /var/run/sockets/server.sock

Client

    docker run --rm -it -v /tmp/sockets/:/var/run/sockets -v ${PWD}/app/build/libs/app.jar:/app/app.jar openjdk:19-jdk-oracle java -classpath /app/app.jar EchoClient /var/run/sockets/server.sock




