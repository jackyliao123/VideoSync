import json.JSONArray;
import json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;

public class VideoSyncServer {

	public static JPanel pClients;

	public static JFrame frame;

	public static final HashSet<ClientHandler> handlers = new HashSet<>();

	public static File videos = new File("videos");

	public static MPVInterface mpvInterface;
	public static MPVReadThread readThread;
	public static ClientStatusGUI serverGUI;

	public static JProgressBar seekBar;

	public static double totalTime;
	public static double currentTime;
	public static boolean paused;
	public static String filePlaying;

	public static boolean pausedBeforeDragging;

	public static void addClient(ClientHandler client) {
		synchronized(handlers) {
			if(filePlaying != null) {
				client.sendCommand("{\"command\": [\"set_property\", \"pause\", true]}");
				client.sendCommand(new JSONObject().put("command", new JSONArray(new String[] {"loadfile", filePlaying})).toString());
				sleep(500);
				client.sendCommand("{\"command\": [\"set_property\", \"playback-time\", " + (currentTime + (paused ? 0 : 0.1)) + "]}");
				if(!paused) {
					sleep(100);
					client.sendCommand("{\"command\": [\"set_property\", \"pause\", false]}");
				}
			}
			handlers.add(client);
			pClients.add(client.gui);
			frame.revalidate();
			frame.repaint();
		}
	}

	public static void removeClient(ClientHandler client) {
		synchronized(handlers) {
			handlers.remove(client);
			pClients.remove(client.gui);
			frame.revalidate();
			frame.repaint();
		}
	}

	public static void broadcastCommand(String command, boolean serverOnly) {
		synchronized(handlers) {
			if(!serverOnly) {
				for(ClientHandler handler : handlers) {
					handler.sendCommand(command);
				}
			}
			mpvInterface.sendJSONCommand(command);
		}
	}

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch(InterruptedException e) {
		}
	}

	public static void broadcastPause(boolean pause) {
		broadcastCommand("{\"command\": [\"set_property\", \"pause\", " + pause + "]}", false);
	}

	public static void broadcastSeek(double time, boolean serverOnly) {
		broadcastCommand("{\"command\": [\"set_property\", \"playback-time\", " + time + "]}", serverOnly);
	}

	public static void broadcastStop() {
		broadcastCommand("{\"command\": [\"stop\"]}", false);
	}

	public static void broadcastLoad(String filename) {
		broadcastCommand("{\"command\": [\"set\", \"speed\", \"1\"]}", false);
		broadcastCommand(new JSONObject().put("command", new JSONArray(new String[] {"loadfile", filename})).toString(), false);
	}

	public static String format(double time) {
		int v = (int) time;
		int s = v % 60;
		v /= 60;
		int m = v % 60;
		v /= 60;
		return String.format("%d:%02d:%02d", v, m, s);
	}

	public static JList<String> lVideos;

	public static void loadFileList() {
		String[] fileList = videos.list();
		Arrays.sort(fileList);
		lVideos.setListData(fileList);
	}

	public static void main(String[] args) throws IOException {
		ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));

		frame = new JFrame("Video Sync");

		lVideos = new JList<>();

		JPanel pVideos = new JPanel(new BorderLayout());
		pVideos.add(new JScrollPane(lVideos));
		loadFileList();

		JButton play = new JButton("Play");
		play.addActionListener(e -> new Thread(() -> {
			broadcastPause(true);
			broadcastLoad("videos/" + lVideos.getSelectedValue());
			sleep(500);
			String s = lVideos.getSelectedValue().toLowerCase();
			boolean isImage = s.endsWith("jpg") || s.endsWith("jpeg") || s.endsWith("png");
			broadcastPause(isImage);
		}).start());

		pVideos.add(play, "South");

		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> loadFileList());

		pVideos.add(refresh, "North");

		frame.add(pVideos, "West");

		pClients = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JScrollPane scClients = new JScrollPane(pClients);
		scClients.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scClients.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		JPanel filler = new JPanel();
		filler.setPreferredSize(new Dimension(0, 400));
		pClients.add(filler);
		frame.add(scClients, "South");

		JPanel pVideo = new JPanel(new BorderLayout());

		JPanel pControl = new JPanel(new BorderLayout());
		JPanel pControlButtons = new JPanel(new GridLayout(1, 3));

		JButton bPause = new JButton("Pause");
		bPause.addActionListener(e -> broadcastPause(true));
		JButton bUnpause = new JButton("Unpause");
		bUnpause.addActionListener(e -> broadcastPause(false));
		JButton bStop = new JButton("Stop");
		bStop.addActionListener(e -> broadcastStop());

		pControlButtons.add(bPause);
		pControlButtons.add(bUnpause);
		pControlButtons.add(bStop);

		pControl.add(pControlButtons, "West");
		seekBar = new JProgressBar();
		seekBar.setMaximum(1000000000);
		seekBar.setStringPainted(true);
		seekBar.setString(format(0));

		seekBar.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent e) {
				broadcastSeek(e.getX() * totalTime / seekBar.getWidth(), true);
			}
		});

		seekBar.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				pausedBeforeDragging = paused;
				broadcastPause(true);
				broadcastSeek(e.getX() * totalTime / seekBar.getWidth(), true);
			}
			public void mouseReleased(MouseEvent e) {
				new Thread(() -> {
					broadcastSeek(e.getX() * totalTime / seekBar.getWidth(), false);
					sleep(100);
					broadcastPause(pausedBeforeDragging);
				}).start();
			}
		});

		pControl.add(seekBar);
		pVideo.add(pControl, "South");

		Canvas video = new Canvas();

		serverGUI = new ClientStatusGUI();
		pVideo.add(serverGUI, "East");
		pVideo.add(video);

		frame.add(pVideo);

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);

		try {
			Class xwindow = Class.forName("sun.awt.X11.XBaseWindow");
			Field field = xwindow.getDeclaredField("window");
			field.setAccessible(true);
			mpvInterface = new MPVInterface(new String[] {"--wid=" + field.get(video.getPeer()), "--vo=x11"});
			mpvInterface.start();

			readThread = new MPVReadThread();
			readThread.start();
		} catch(Exception e) {
			System.err.println("Cannot play video");
			e.printStackTrace();
		}

		try {
			while(true) {
				Socket socket = serverSocket.accept();
				ClientHandler handler = new ClientHandler(socket);
				ClientStatusGUI gui = new ClientStatusGUI(handler);
				addClient(handler);
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public static class MPVReadThread extends Thread {
		public void run() {
			while(true) {
				String str = mpvInterface.readJSONMessage();
				JSONObject obj = new JSONObject(str);
				if(obj.has("id")) {
					int id = obj.getInt("id");
					if(id == 1) {
						currentTime = obj.optDouble("data", 0);
					} else if(id == 2) {
						totalTime = obj.optDouble("data", 1);
					} else if(id == 3) {
						paused = obj.optBoolean("data", false);
					} else if(id == 4) {
						filePlaying = obj.optString("data", null);
					}
					seekBar.setValue((int) (currentTime / totalTime * 1000000000));
					seekBar.setString(format(currentTime));
					seekBar.repaint();
				}
				serverGUI.response(str);
			}
		}
	}

}

