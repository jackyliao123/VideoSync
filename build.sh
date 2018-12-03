#!/bin/bash

mkdir build
javac -d ./build ./src/*.java ./src/json/*.java -cp junixsocket-1.3.jar
jar cvf sync.jar -C build .
