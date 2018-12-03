import json.JSONObject;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.InetSocketAddress;

public class ClientStatusGUI extends JPanel {
	public JTextPane textpane;
	public JScrollPane scrollPane;
	public StyledDocument doc;

	public boolean running = true;

	public String[] levels = {"fatal", "error", "warn", "info", "v", "debug", "trace"};

	public Style[] styles = new Style[levels.length];
	public Color[] foreground = {
			new Color(0xff4000),
			new Color(0xffffff),
			new Color(0x804000),
			new Color(0x000000),
			new Color(0x004000),
			new Color(0x808080),
			new Color(0xc0c0c0),
	};
	public Color[] background = {
			new Color(0x000000),
			new Color(0xff0000),
			new Color(0xffff00),
			new Color(0xffffff),
			new Color(0xffffff),
			new Color(0xffffff),
			new Color(0xffffff),
	};

	public int[] lut = new int[128];

	public ClientHandler handler;

	public GraphPanel graphPanel;

	public ClientStatusGUI(ClientHandler handler) {
		this();

		for(int i = 0; i < 400; ++i) {
			offsets.addLast(0.0);
		}

		this.handler = handler;
		handler.gui = this;
		handler.start();

		graphPanel = new GraphPanel();
		graphPanel.setPreferredSize(new Dimension(400, 100));
		add(graphPanel, "North");

		new PingThread().start();
		new SyncThread().start();

		InetSocketAddress addr = (InetSocketAddress) handler.socket.getRemoteSocketAddress();
		setBorder(BorderFactory.createTitledBorder(addr.getAddress().getHostAddress() + "    :    " + addr.getPort()));

		JPanel actions = new JPanel(new GridLayout(1, 4));
		JButton shell = new JButton("Shell");
		JButton checkFiles = new JButton("Sync Files");
		JButton autoSync = new JButton("Sync ON");
		JButton kill = new JButton("Kill");

		shell.addActionListener(e -> {
			try {
				Runtime.getRuntime().exec("bash scripts/shell " + addr.getAddress().getHostAddress());
			} catch(IOException ex) {
				ex.printStackTrace();
			}
		});

		checkFiles.addActionListener(e -> {
			try {
				Runtime.getRuntime().exec("bash scripts/sync " + addr.getAddress().getHostAddress());
			} catch(IOException ex) {
				ex.printStackTrace();
			}
		});

		autoSync.addActionListener(e -> {
			autosync = !autosync;
			if(!autosync) {
				handler.sendCommand("{\"command\": [\"set\", \"speed\", \"1\"]}");
			}
			autoSync.setText("Sync " + (autosync ? "ON" : "OFF"));
		});
		kill.addActionListener(e -> handler.disconnect());

		actions.add(shell);
		actions.add(checkFiles);
		actions.add(autoSync);
		actions.add(kill);

		add(actions, "South");
	}

	public ClientStatusGUI() {
		super(new BorderLayout());

		textpane = new JTextPane();
		textpane.setEditable(false);
		doc = textpane.getStyledDocument();
		scrollPane = new JScrollPane(textpane);
		autoscroll(textpane);

		for(int i = 0; i < styles.length; ++i) {
			styles[i] = textpane.addStyle(levels[i], null);
			StyleConstants.setForeground(styles[i], foreground[i]);
			StyleConstants.setBackground(styles[i], background[i]);
			lut[levels[i].charAt(0)] = i;
		}

		add(scrollPane);

		setMaximumSize(new Dimension(400, 400));
		setMinimumSize(new Dimension(400, 400));
		setPreferredSize(new Dimension(400, 400));
		setSize(new Dimension(400, 400));
	}

	public void newLogMessage(String level, String prefix, String msg) {
		msg = msg.trim();
		int ind = lut[level.charAt(0) % 128];
		try {
			doc.insertString(doc.getLength(), "[" + prefix + "] " + msg + "\n", styles[ind]);
		} catch(BadLocationException e) {
		}
		JScrollBar vertical = scrollPane.getVerticalScrollBar();
		vertical.setValue(vertical.getMaximum() + 10000);
	}

	public void response(String res) {
		JSONObject obj = new JSONObject(res);
		if(obj.has("event") && obj.getString("event").equals("log-message")) {
			newLogMessage(obj.getString("level"), obj.getString("prefix"), obj.getString("text"));
		}
		if(obj.has("id")) {
			int id = obj.getInt("id");
			if(id == 1) {
				double time = obj.optDouble("data", 0);
				double timeDiff = time - VideoSyncServer.currentTime;
				frameUpdate(timeDiff);
			}
		}
	}

	public static void autoscroll(JTextComponent textArea) {
		final JScrollPane scrollPane = (JScrollPane) (textArea.getParent().getParent());
		((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			private int val = 0;
			private int ext = 0;
			private int max = 0;
			private final BoundedRangeModel model = scrollPane.getVerticalScrollBar().getModel();
			public void adjustmentValueChanged(AdjustmentEvent e) {
				int newMax = model.getMaximum();
				if (newMax != max && (val + ext == max) ) {
					model.setValue(model.getMaximum() - model.getExtent());
				}
				val = model.getValue();
				ext = model.getExtent();
				max = model.getMaximum();
			}
		});
	}

	public ArrayDequeRandomAccess<Double> offsets = new ArrayDequeRandomAccess<>();
	public int packetCount = 0;
	public double lastOffset = 0;
	public double averageOffset;

	public void frameUpdate(double offset) {
		if(handler == null) {
			return;
		}
		synchronized(offsets) {
			offsets.removeFirst();
			offsets.addLast(offset);
			lastOffset = offset;
			double agg = 0;
			for(int i = 375; i < 400; ++i) {
				agg += offsets.get(i);
			}
			agg /= 25.0;
			double agg2 = 0;
			int n = 0;
			for(int i = 375; i < 400; ++i) {
				double v = offsets.get(i);
				if(Math.abs(v - agg) < 0.1) {
					agg2 += v;
					n++;
				}
			}
			if(n != 0) {
				averageOffset = agg2 / n;
			}
			++packetCount;
			graphPanel.repaint();
		}
	}

	public long pingId;
	public long lastPing;
	public long pingSeqStart;
	public ArrayDequeRandomAccess<Long> pingSeq = new ArrayDequeRandomAccess<>();

	public void pong(long data) {
		long time = System.nanoTime();
		synchronized(pingSeq) {
			if(data >= pingSeqStart && data < pingSeqStart + pingSeq.size()) {
				int ind = (int) (data - pingSeqStart);
				long d = time - pingSeq.get(ind);
				lastPing = d;
				pingSeq.set(ind, -d);
			}
		}
		graphPanel.repaint();
	}

	public class PingThread extends Thread {
		public void run() {
			while(running) {
				synchronized(pingSeq) {
					handler.ping(pingId++);
					pingSeq.add(System.nanoTime());
					if(pingSeq.size() > 100) {
						pingSeq.removeFirst();
						++pingSeqStart;
					}
				}
				graphPanel.repaint();
				VideoSyncServer.sleep(100);
			}
		}
	}

	public boolean autosync = true;

	public class SyncThread extends Thread {
		public void run() {
			while(running) {
				if(autosync) {
					if(Math.abs(averageOffset) > 1) {
						handler.sendCommand("{\"command\": [\"set_property\", \"playback-time\", " + VideoSyncServer.currentTime + "]}");
					} else {
						handler.sendCommand("{\"command\": [\"set\", \"speed\", \"" + (1 - averageOffset * 0.5) + "\"]}");
					}
				}
				VideoSyncServer.sleep(500);
			}
		}
	}

	public class GraphPanel extends JPanel {
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(Color.red);
			g2d.draw(new Line2D.Double(0, 25, 400, 25));
			g2d.setColor(Color.gray);
			for(int i = 0; i < 4; ++i) {
				double x = (i + 1) * 100 - packetCount % 100;
				g2d.draw(new Line2D.Double(x, 0, x, 50));

				x = (i + 1) * 100 - (int) (pingSeqStart * 4 % 100);
				g2d.draw(new Line2D.Double(x, 50, x, 100));
			}

			synchronized(pingSeq) {
				int i = 0;
				for(double d : pingSeq) {
					double x = i * 4;
					if(d < 0) {
						g2d.setColor(new Color(128, 128, 255));
					} else {
						g2d.setColor(Color.red);
						d = -d;
					}
					double y = 100 + d / 1e6;
					y = Math.max(y, 0);
					g2d.fill(new Rectangle2D.Double(x, y, 4, 100));
					i++;
				}
			}
			g2d.setColor(Color.black);
			g2d.drawString("Ping: " + String.format("%.2f", lastPing / 1e6) + "ms", 5, 65);

			g2d.setColor(Color.black);
			Path2D.Double path = new Path2D.Double();
			synchronized(offsets) {
				int i = 0;
				for(double d : offsets) {
					double x = i;
					double y = 25 - d * 100;
					if(i == 0) {
						path.moveTo(x, y);
					} else {
						path.lineTo(x, y);
					}
					i++;
				}
			}
			g2d.drawString("Offset: " + (int) (lastOffset * 1000.0) + "ms", 5, 15);
			g2d.draw(path);
		}
	}

}

