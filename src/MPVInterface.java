import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class MPVInterface {

	public String[] arguments;

	public Process process;
	public String ipcPath;
	public File ipcFile;

	public BufferedReader udsInput;
	public OutputStream udsOutput;

	public AFUNIXSocket udsClient;

	public MPVInterface(String[] args) {
		arguments = args;
	}

	public void start() throws IOException {

		System.out.println("Attempting to start");

		File tmp = new File("tmp");

		if(!tmp.exists()) {
			tmp.mkdir();
		}

		ipcPath = tmp.getName() + File.separator + UUID.randomUUID().toString();
		ipcFile = new File(ipcPath);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Destroy");
			if(process != null) {
				process.destroy();
			}
			ipcFile.delete();
		}));

		ArrayList<String> commands = new ArrayList<>();
		commands.add("mpv");
		commands.add("--osc=no");
		commands.add("--osd-level=0");
		commands.add("--input-vo-keyboard=no");
		commands.add("--no-input-default-bindings");
		commands.add("--no-input-terminal");
		commands.add("--terminal=no");
		commands.add("--idle");
		commands.add("--hr-seek=yes");
		commands.add("--fs");
		commands.add("--input-ipc-server=" + ipcPath);
		if(arguments != null) {
			commands.addAll(Arrays.asList(arguments));
		}
		ProcessBuilder builder = new ProcessBuilder(commands);
		process = builder.start();

		while(!ipcFile.exists()) {
			try {
				Thread.sleep(20);
			} catch(InterruptedException e) {
			}
		}

		udsClient = AFUNIXSocket.newInstance();
		udsClient.connect(new AFUNIXSocketAddress(ipcFile));
		udsInput = new BufferedReader(new InputStreamReader(udsClient.getInputStream()));
		udsOutput = udsClient.getOutputStream();

		sendJSONCommand("{\"command\": [\"request_log_messages\", \"info\"]}");
		sendJSONCommand("{\"command\": [\"observe_property\", 1, \"playback-time\"]}");
		sendJSONCommand("{\"command\": [\"observe_property\", 2, \"duration\"]}");
		sendJSONCommand("{\"command\": [\"observe_property\", 3, \"pause\"]}");
		sendJSONCommand("{\"command\": [\"observe_property\", 4, \"path\"]}");

		System.out.println("UDS connection successful");

		new Thread(() -> {
			while(true) {
				try {
					System.out.println("mpv exited with code " + process.waitFor());
					System.exit(-1);
					break;
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public synchronized void sendJSONCommand(String s) {
		try {
			udsOutput.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		} catch(IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
	}

	public String readJSONMessage() {
		try {
			String line = udsInput.readLine();
			if(line == null) {
				throw new IOException("No line to read");
			}
			return line;
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	public void kill() {
		process.destroy();
		System.exit(-1);
	}
}

