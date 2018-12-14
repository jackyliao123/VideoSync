# VideoSync

This is a tool to allow synchronized video playback over the network utilizing the [mpv](https://mpv.io/) video player. It has a server and a client.

The server has a GUI, and controls the video playback of all the connected clients. The server changes the playback speed of the clients to obtain a better synchronization across them.

### System requirements
- Linux
- Xorg
- Java 8
- [mpv](https://mpv.io/) installed and is able to play videos smoothly
- x86/x64 architecture (Optional: other architectures require compilation of [junixsocket](https://github.com/kohlschutter/junixsocket))
- `rsync`, `ssh`, `termite` (Optional: Used for pushing video files over the network. This requires further setup)

### Building and running
`javac` and `jar` needs to be installed and in the `PATH`.

#### Building:

    ./build.sh

#### Running:
Videos should be placed in the `videos` directory for both the client and the server. 

Running server:

    ./start_server.sh [listen port]

Running client:

    ./start_client.sh [server address] [server port]

*Notes*: 
- For the current setup, the audio will only be played on the server. This behaviour can be changed by modifying the arguments passed to [mpv](https://mpv.io/)  in `VideoSyncServer.java` and `VideoSyncClient.java`.
- The `scripts` directory contain some scripts that can be called within the server GUI. They need to be manually customized for your setup. For all the scripts, the first argument passed in is the IP address of the client.

### To do
- Better build system
- Easier setup process
- Integrated file pushing
- Encrypted connections
- Better synchronization for connections with >500ms ping
