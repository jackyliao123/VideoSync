#!/bin/bash

if [ ! $# -eq 1 ]; then
	echo "Usage: $0 [listen port]"
	exit 1
fi

java -Djava.library.path=lib-native -cp sync.jar:junixsocket-1.3.jar VideoSyncServer $1
