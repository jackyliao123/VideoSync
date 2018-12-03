#!/bin/bash

if [ ! $# -eq 2 ]; then
	echo "Usage: $0 [server address] [server port]"
	exit 1
fi

java -Djava.library.path=lib-native -cp sync.jar:junixsocket-1.3.jar VideoSyncClient $1 $2
