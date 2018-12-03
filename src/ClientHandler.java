import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

public class ClientHandler {

	public Socket socket;

	public WriteThread writeThread;
	public ReadThread readThread;

	public ClientStatusGUI gui;

	public ClientHandler(Socket socket) {
		this.socket = socket;
	}

	public void start() {
		writeThread = new WriteThread();
		writeThread.start();
		readThread = new ReadThread();
		readThread.start();
	}

	public class ReadThread extends Thread {
		public DataInputStream stream;
		public void run() {
			try {
				stream = new DataInputStream(socket.getInputStream());
				while(true) {
					Packet packet = new Packet();
					packet.id = stream.read();
					int length = stream.readInt();
					packet.content = new byte[length];
					stream.readFully(packet.content);
					try {
						processPacket(packet);
					} catch(Exception e) {
						System.err.println("Error occurred while processing packet " + packet.id + " (" + packet.content.length + ")");
						e.printStackTrace();
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
				disconnect();
			}
		}
	}
	public class WriteThread extends Thread {
		public DataOutputStream stream;
		public ArrayBlockingQueue<Packet> writeQueue = new ArrayBlockingQueue<>(4096);
		public void sendPacket(Packet packet) {
			try {
				writeQueue.put(packet);
			} catch(InterruptedException e) {
			}
		}
		public void run() {
			try {
				stream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
				while(true) {
					Packet data = writeQueue.take();
					stream.write(data.id);
					stream.writeInt(data.content.length);
					stream.write(data.content);
					stream.flush();
				}
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
				disconnect();
			}
		}
	}

	public void disconnect() {
		try {
			socket.close();
		} catch(IOException e) {
		}
		writeThread.interrupt();
		gui.running = false;
		VideoSyncServer.removeClient(this);
	}

	public void processPacket(Packet packet) {
		switch(packet.id) {
			case 0:
				gui.pong(ByteBuffer.wrap(packet.content).getLong());
				break;
			case 1:
				gui.response(new String(packet.content, StandardCharsets.UTF_8));
				break;
		}
	}

	public void ping(long data) {
		writeThread.sendPacket(new Packet(0, ByteBuffer.allocate(8).putLong(data).array()));
	}

	public void sendCommand(String command) {
		writeThread.sendPacket(new Packet(1, command.getBytes(StandardCharsets.UTF_8)));
	}

}
