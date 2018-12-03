import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

public class VideoSyncClient {

	public static Socket socket;

	public static ReadThread readThread;
	public static WriteThread writeThread;

	public static MPVReadThread mpvReadThread;
	public static MPVWriteThread mpvWriteThread;

	public static MPVInterface mpvInterface;

	public static void main(String[] args) {
		try {
			socket = new Socket(args[0], Integer.parseInt(args[1]));

			mpvInterface = new MPVInterface(new String[] {"--ao=null"});

			mpvReadThread = new MPVReadThread();
			mpvWriteThread = new MPVWriteThread();
			readThread = new ReadThread();
			writeThread = new WriteThread();

			mpvInterface.start();

			mpvWriteThread.start();
			writeThread.start();

			mpvReadThread.start();

			readThread.start();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static void killClient() {
		try {
			writeThread.interrupt();
			socket.close();
		} catch(IOException ex) {}
		mpvInterface.kill();
	}

	public static class ReadThread extends Thread {
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
						System.err.println("Error occurred when processing packet " + packet.id + " (" + packet.content.length + ")");
						e.printStackTrace();
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
				killClient();
			}
		}
	}
	public static class WriteThread extends Thread {
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
				killClient();
			}
		}
	}

	public static void processPacket(Packet packet) throws Exception {
		switch(packet.id) {
			case 0:
				writeThread.sendPacket(new Packet(0, packet.content));
				break;
			case 1:
				mpvWriteThread.sendCommand(new String(packet.content, StandardCharsets.UTF_8));
				break;
		}

	}

	public static class MPVReadThread extends Thread {
		public void run() {
			while(true) {
				String message = mpvInterface.readJSONMessage();
				writeThread.sendPacket(new Packet(1, message.getBytes(StandardCharsets.UTF_8)));
			}
		}
	}

	public static class MPVWriteThread extends Thread {
		public ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(4096);
		public void sendCommand(String command) {
			try {
				commandQueue.put(command);
			} catch(InterruptedException e) {
			}
		}
		public void run() {
			while(true) {
				try {
					mpvInterface.sendJSONCommand(commandQueue.take());
				} catch(InterruptedException e) {
				}
			}
		}
	}
}
