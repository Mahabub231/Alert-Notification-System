package com.mycompany.notification;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientFrame().setVisible(true));
    }
}

class ClientFrame extends JFrame {
    private JTextField txtServerHost = new JTextField("localhost", 12);
    private JTextField txtServerPort = new JTextField("5050", 5);
    private JTextField txtName = new JTextField(10);
    private JButton btnConnect = new JButton("Connect");
    private JButton btnDisconnect = new JButton("Disconnect");

    private DefaultListModel<String> msgModel = new DefaultListModel<>();
    private JList<String> msgList = new JList<>(msgModel);
    private JTextArea txtMessage = new JTextArea(4, 30);
    private JButton btnSend = new JButton("Send");

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected = false;

    private static final String NAME_FILE = "client_counter.txt";
    private int countSentMsg = 0;

    ClientFrame() {
        super("Alert & Notification System : Client Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        txtName.setText("Client" + getNextClientNumber());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Name:"));
        top.add(txtName);
        top.add(new JLabel("Server:"));
        top.add(txtServerHost);
        top.add(txtServerPort);
        top.add(btnConnect);
        top.add(btnDisconnect);
        btnDisconnect.setEnabled(false);

        msgList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(msgList);

        JPanel bottom = new JPanel(new BorderLayout());
        txtMessage.setLineWrap(true);
        txtMessage.setWrapStyleWord(true);
        bottom.add(new JScrollPane(txtMessage), BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        btnConnect.addActionListener(e -> connect());
        btnDisconnect.addActionListener(e -> disconnect());
        btnSend.addActionListener(e -> sendMessage());
    }

    private int getNextClientNumber() {
        int number = 1;
        try {
            File file = new File(NAME_FILE);
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line = br.readLine();
                if (line != null) {
                    try { number = Integer.parseInt(line.trim()) + 1; } 
                    catch (NumberFormatException ignored) { number = 1; }
                }
                br.close();
            }
            PrintWriter pw = new PrintWriter(new FileWriter(file));
            pw.println(number);
            pw.close();
        } catch (IOException e) { e.printStackTrace(); }
        return number;
    }

    private void connect() {
        String host = txtServerHost.getText().trim();
        int port;
        try { port = Integer.parseInt(txtServerPort.getText().trim()); }
        catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Invalid port"); return; }

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(socket.getOutputStream(), true);

            writer.println(txtName.getText().trim());
            connected = true;

            new Thread(this::readMessages).start();

            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            txtName.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Connected to server");

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage());
        }
    }

    private void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
        txtName.setEnabled(true);
        JOptionPane.showMessageDialog(this, "Disconnected from server");
    }

    private void sendMessage() {
        if (!connected) return;
        String msg = txtMessage.getText().trim();
        if (!msg.isEmpty()) {
            String fullMsg = txtName.getText().trim() + "\t" + msg;
            writer.println(fullMsg);
            countSentMsg++;
            msgModel.addElement("Sent Msg → " + msg);
            txtMessage.setText("");
        }
    }

    private void readMessages() {
        try {
            String line;
            while (connected && reader != null && (line = reader.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> {
                    msgModel.addElement("Server: " + msg);
                    showTemporaryPopup("Server Reply:\n" + msg);
                });
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> 
                    JOptionPane.showMessageDialog(this, "Connection lost: " + e.getMessage()));
                disconnect();
            }
        }
    }

    private void showTemporaryPopup(String msg) {
        JDialog dialog = new JDialog(this, "Server Reply", false);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        JLabel label = new JLabel("<html><body style='padding:10px;'>" + msg + "</body></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        dialog.add(label, BorderLayout.CENTER);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);

        // Auto-close after 3 seconds
        new Timer(3000, e -> dialog.dispose()).start();
    }
}
