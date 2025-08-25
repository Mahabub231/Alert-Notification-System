package com.mycompany.notification;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ServerApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}

class ServerFrame extends JFrame {
    private static final int DEFAULT_PORT = 5050;

    private final JLabel statusLabel = new JLabel("Server: Stopped");
    private final JTextField portField = new JTextField(String.valueOf(DEFAULT_PORT), 6);
    private final JButton startBtn = new JButton("Start");
    private final JButton stopBtn = new JButton("Stop");

    private final DefaultListModel<AlertItem> alertModel = new DefaultListModel<>();
    private final JList<AlertItem> alertList = new JList<>(alertModel);
    private final JTextArea detailsArea = new JTextArea();

    private final JLabel totalCountLabel = new JLabel("Total: 0");
    private final JLabel unreadCountLabel = new JLabel("Unread: 0");
    private final JButton markReadBtn = new JButton("Mark Selected Read");
    private final JButton markAllReadBtn = new JButton("Mark All Read");
    private final JCheckBox beepCheck = new JCheckBox("Beep on New", true);
    private final JCheckBox popupCheck = new JCheckBox("Popup on New", false);
    private final JButton replyBtn = new JButton("Reply to Client");

    private volatile ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;

    // Map to store clients with unique key: host:port
    private final Map<String, PrintWriter> clientMap = Collections.synchronizedMap(new HashMap<>());
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    ServerFrame() {
        super("Alert & Notification System – Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        // Top panel
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Port:"));
        top.add(portField);
        top.add(startBtn);
        top.add(stopBtn);
        top.add(statusLabel);

        // Alert list + details split pane
        alertList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alertList.setCellRenderer(new AlertCellRenderer());
        JScrollPane listScroll = new JScrollPane(alertList);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailsScroll);
        split.setDividerLocation(500);

        // Bottom panel
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(totalCountLabel);
        row1.add(Box.createHorizontalStrut(10));
        row1.add(unreadCountLabel);
        row1.add(Box.createHorizontalStrut(15));
        row1.add(markReadBtn);
        row1.add(markAllReadBtn);
        bottom.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(beepCheck);
        row2.add(popupCheck);
        bottom.add(row2);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(replyBtn);
        bottom.add(row3);

        // Root layout
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        // Actions
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
        markReadBtn.addActionListener(e -> markSelectedRead());
        markAllReadBtn.addActionListener(e -> markAllRead());

        alertList.addListSelectionListener(e -> {
            AlertItem item = alertList.getSelectedValue();
            detailsArea.setText(item != null ? renderDetails(item) : "");
        });

        replyBtn.addActionListener(e -> openReplyDialog());
        stopBtn.setEnabled(false);
    }

    private String renderDetails(AlertItem a) {
        return "Time: " + sdf.format(new Date(a.timestamp)) + "\n"
                + "From: " + a.sender + "\n"
                + "ClientKey: " + a.clientKey + "\n"
                + "Read: " + (a.read ? "Yes" : "No") + "\n\n"
                + "Message:\n" + a.message;
    }

    private void startServer() {
        if (running) return;
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port.");
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            pool = Executors.newCachedThreadPool();
            pool.execute(this::acceptLoop);
            statusLabel.setText("Server: Running on port " + port);
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            portField.setEnabled(false);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to start: " + ex.getMessage());
        }
    }

    private void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (pool != null) pool.shutdownNow();
        statusLabel.setText("Server: Stopped");
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        portField.setEnabled(true);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket sock = serverSocket.accept();
                pool.execute(new ClientHandler(sock, this));
            } catch (IOException e) {
                if (running) SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Accept error: " + e.getMessage()));
            }
        }
    }

    void onNewAlert(AlertItem item) {
        SwingUtilities.invokeLater(() -> {
            alertModel.addElement(item);
            updateCounts();
            if (beepCheck.isSelected()) Toolkit.getDefaultToolkit().beep();
            if (popupCheck.isSelected()) {
                JOptionPane.showMessageDialog(this,
                        "From: " + item.sender + "\n" + item.message,
                        "New Alert",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    void updateCounts() {
        int total = alertModel.size();
        int unread = 0;
        for (int i = 0; i < total; i++) if (!alertModel.get(i).read) unread++;
        totalCountLabel.setText("Total: " + total);
        unreadCountLabel.setText("Unread: " + unread);
        alertList.repaint();
    }

    private void markSelectedRead() {
        AlertItem sel = alertList.getSelectedValue();
        if (sel != null && !sel.read) {
            sel.read = true;
            updateCounts();
            detailsArea.setText(renderDetails(sel));
        }
    }

    private void markAllRead() {
        for (int i = 0; i < alertModel.size(); i++) alertModel.get(i).read = true;
        updateCounts();
    }

    void addClient(String clientKey, PrintWriter writer) { clientMap.put(clientKey, writer); }
    void removeClient(String clientKey) { clientMap.remove(clientKey); }

    private void openReplyDialog() {
        AlertItem selected = alertList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a client alert first.");
            return;
        }

        JDialog dialog = new JDialog(this, "Reply to " + selected.sender, true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JTextArea replyArea = new JTextArea();
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        dialog.add(new JScrollPane(replyArea), BorderLayout.CENTER);

        JButton sendBtn = new JButton("Send");
        dialog.add(sendBtn, BorderLayout.SOUTH);

        sendBtn.addActionListener(ev -> {
            String msg = replyArea.getText().trim();
            if (!msg.isEmpty()) {
                PrintWriter out = clientMap.get(selected.clientKey);
                if (out != null) {
                    out.println("Server Reply: " + msg);

                    AlertItem replyItem = new AlertItem(System.currentTimeMillis(),
                            "Server (reply to " + selected.sender + ")", msg, selected.clientKey);
                    onNewAlert(replyItem);
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Client not available.");
                }
            }
        });

        dialog.setVisible(true);
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerFrame server;
    private String clientKey;

    ClientHandler(Socket socket, ServerFrame server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // First message from client is its name
            String clientName = br.readLine();
            clientKey = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            server.addClient(clientKey, out);

            String line;
            while ((line = br.readLine()) != null) {
                String msg = line;
                // If client still sends name in message, remove prefix
                int tab = line.indexOf('\t');
                if (tab >= 0) {
                    msg = line.substring(tab + 1).trim();
                }
                AlertItem item = new AlertItem(System.currentTimeMillis(), clientName, msg, clientKey);
                server.onNewAlert(item);
            }
        } catch (IOException ignored) {}
        finally {
            try { socket.close(); } catch (IOException ignored) {}
            server.removeClient(clientKey);
        }
    }
}

class AlertItem {
    final long timestamp;
    final String sender;
    final String message;
    final String clientKey;
    boolean read = false;

    AlertItem(long timestamp, String sender, String message, String clientKey) {
        this.timestamp = timestamp;
        this.sender = (sender == null || sender.isEmpty()) ? "Unknown" : sender;
        this.message = message == null ? "" : message;
        this.clientKey = clientKey == null ? "" : clientKey;
    }

    @Override
    public String toString() {
        String prefix = read ? "   " : "● ";
        String displayMsg = message.length() > 50 ? message.substring(0, 50) + "..." : message;
        return prefix + "[" + sender + "] " + displayMsg;
    }
}

class AlertCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof AlertItem) {
            AlertItem a = (AlertItem) value;
            setText(a.toString());
            setFont(getFont().deriveFont((!a.read && !isSelected) ? Font.BOLD : Font.PLAIN));
        }
        return c;
    }
}
