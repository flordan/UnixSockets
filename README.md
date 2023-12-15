# UnixSockets
Library for Communicating trhough UnixScokets and NIO


# Compilation
docker run -it --rm -v "$PWD":/home/gradle/project -w /home/gradle/project gradle:8.5.0-jdk17-jammy gradle clean build

# Test
On baremetal java:
java -classpath app/build/libs/app.jar EchoServer /tmp/path.sock
java -classpath app/build/libs/app.jar EchoClient /tmp/path.sock
