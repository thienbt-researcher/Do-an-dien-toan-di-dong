package com.chatapp.server;

import com.chatapp.dao.MessageDAO;
import com.chatapp.dao.UserDAO;
import com.chatapp.model.Message;
import com.chatapp.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Runs the chat server and provides the host login/control window. */
public class ChatServer {

    public static final int DEFAULT_PORT = 5000;

    private static final String HOST_USERNAME = "admin1";
    private static final String HOST_PASSWORD = "admin1";
    private static volatile Consumer<String> hostLogger = System.out::println;

    private final int port;
    private volatile ServerSocket serverSocket;
    private volatile Thread serverThread;
    private volatile boolean starting;
    private volatile boolean running;
    private volatile boolean stopRequested;

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port phai nam trong khoang 1-65535.");
        }
        this.port = port;
    }

    public void start() throws IOException {
        runServer();
    }

    public synchronized void startAsync() {
        if (serverThread != null && serverThread.isAlive()) {
            throw new IllegalStateException("Chat Server dang chay hoac dang khoi dong.");
        }

        stopRequested = false;
        starting = true;
        serverThread = new Thread(() -> {
            try {
                runServer();
            } catch (IOException e) {
                log("Khong the khoi dong Chat Server: " + e.getMessage());
            } finally {
                starting = false;
                running = false;
            }
        }, "chat-server-acceptor");
        serverThread.start();
    }

    private void runServer() throws IOException {
        boolean startedSuccessfully = false;
        try (ServerSocket listener = new ServerSocket()) {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(port));
            serverSocket = listener;

            if (stopRequested) {
                return;
            }

            starting = false;
            running = true;
            startedSuccessfully = true;
            log("Chat Server dang lang nghe tai cong " + port + ".");

            while (running && !stopRequested) {
                try {
                    Socket clientSocket = listener.accept();
                    Thread clientThread = new Thread(
                            new ClientHandler(clientSocket),
                            "chat-client-" + clientSocket.getPort()
                    );
                    clientThread.start();
                } catch (SocketException e) {
                    if (!stopRequested) {
                        throw e;
                    }
                }
            }
        } finally {
            serverSocket = null;
            running = false;
            starting = false;
            if (startedSuccessfully) {
                log("Chat Server da dung.");
            }
        }
    }

    public void stopServer() {
        stopRequested = true;
        running = false;
        ServerSocket listener = serverSocket;
        if (listener != null) {
            try {
                listener.close();
            } catch (IOException e) {
                log("Loi khi dong Chat Server: " + e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStarting() {
        return starting;
    }

    public boolean isThreadAlive() {
        Thread thread = serverThread;
        return thread != null && thread.isAlive();
    }

    public static void setHostLogger(Consumer<String> logger) {
        hostLogger = logger == null ? System.out::println : logger;
    }

    static void log(String message) {
        hostLogger.accept(message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HostLoginWindow().setVisible(true));
    }

    private static class HostLoginWindow extends JFrame {
        private static final Color BACKGROUND = new Color(23, 28, 25);
        private static final Color SURFACE = new Color(35, 42, 38);
        private static final Color TEXT = new Color(231, 239, 233);
        private static final Color MUTED = new Color(183, 196, 187);
        private static final Color ACCENT = new Color(128, 217, 139);
        private static final Color INPUT = new Color(47, 57, 51);
        private static final Color BORDER = new Color(67, 81, 72);

        private final JTextField usernameField = new JTextField(20);
        private final JPasswordField passwordField = new JPasswordField(20);

        private HostLoginWindow() {
            super("Chat Server - Đăng nhập host");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());
            getContentPane().setBackground(BACKGROUND);
            add(createForm(), BorderLayout.CENTER);

            JButton loginButton = createButton("Đăng nhập", true);
            JPanel footer = new JPanel();
            footer.setBackground(SURFACE);
            footer.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
            footer.add(loginButton);
            add(footer, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(loginButton);

            loginButton.addActionListener(event -> login());
            pack();
            setMinimumSize(new Dimension(390, 260));
            setLocationRelativeTo(null);
        }

        private JPanel createForm() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(SURFACE);
            panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 14, 28));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(8, 6, 8, 6);
            constraints.anchor = GridBagConstraints.WEST;

            JLabel title = new JLabel("Đăng nhập quản trị Chat Server");
            title.setForeground(ACCENT);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridwidth = 2;
            panel.add(title, constraints);

            constraints.gridwidth = 1;
            constraints.gridy = 1;
            panel.add(createLabel("Tên đăng nhập"), constraints);
            constraints.gridx = 1;
            styleInput(usernameField);
            panel.add(usernameField, constraints);

            constraints.gridx = 0;
            constraints.gridy = 2;
            panel.add(createLabel("Mật khẩu"), constraints);
            constraints.gridx = 1;
            styleInput(passwordField);
            panel.add(passwordField, constraints);
            return panel;
        }

        private JLabel createLabel(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(MUTED);
            return label;
        }

        private void login() {
            char[] password = passwordField.getPassword();
            boolean accepted = HOST_USERNAME.equals(usernameField.getText().trim())
                    && Arrays.equals(password, HOST_PASSWORD.toCharArray());
            Arrays.fill(password, '\0');

            if (!accepted) {
                JOptionPane.showMessageDialog(this,
                        "Tài khoản hoặc mật khẩu host không đúng.",
                        "Không thể đăng nhập", JOptionPane.ERROR_MESSAGE);
                passwordField.setText("");
                return;
            }

            dispose();
            new HostDashboardWindow().setVisible(true);
        }

        private void styleInput(JTextField field) {
            field.setBackground(INPUT);
            field.setForeground(TEXT);
            field.setCaretColor(ACCENT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        }

        private JButton createButton(String text, boolean primary) {
            JButton button = new JButton(text);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBackground(primary ? ACCENT : INPUT);
            button.setForeground(primary ? BACKGROUND : TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(primary ? ACCENT : BORDER),
                    BorderFactory.createEmptyBorder(8, 16, 8, 16)));
            return button;
        }
    }

    private static class HostDashboardWindow extends JFrame {
        private static final Color BACKGROUND = new Color(23, 28, 25);
        private static final Color SURFACE = new Color(35, 42, 38);
        private static final Color TEXT = new Color(231, 239, 233);
        private static final Color ACCENT = new Color(128, 217, 139);
        private static final Color INPUT = new Color(47, 57, 51);
        private static final Color BORDER = new Color(67, 81, 72);

        private final JTextField portField = new JTextField(
                Integer.toString(DEFAULT_PORT), 7);
        private final JLabel statusLabel = new JLabel("Đã dừng");
        private final JLabel clientCountLabel = new JLabel("0");
        private final JLabel usersCountLabel = new JLabel("Users: 0");
        private final JLabel messagesCountLabel = new JLabel("Tin nhắn: 0");
        private final JTextArea logArea = new JTextArea();
        private final DefaultTableModel usersModel = createModel(
                "ID", "Username", "Họ tên", "Tạo lúc", "Trạng thái");
        private final DefaultTableModel messagesModel = createModel(
                "ID", "Sender ID", "Người gửi", "Nội dung", "Thời gian");
        private final JTable usersTable = new JTable(usersModel);
        private final JTable messagesTable = new JTable(messagesModel);
        private final UserDAO userDAO = new UserDAO();
        private final MessageDAO messageDAO = new MessageDAO();
        private final JButton startButton = createButton("Khởi động server", true);
        private final JButton stopButton = createButton("Dừng server", false);
        private final JButton banButton = createButton("Ban user", false);
        private final Timer refreshTimer;
        private ChatServer server;

        private HostDashboardWindow() {
            super("Chat Server - Bảng điều khiển host");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(620, 430));
            setLayout(new BorderLayout(0, 12));
            getContentPane().setBackground(BACKGROUND);

            add(createHeader(), BorderLayout.NORTH);
            add(createManagementTabs(), BorderLayout.CENTER);
            add(createControls(), BorderLayout.SOUTH);
            startButton.addActionListener(event -> startServer());
            stopButton.addActionListener(event -> stopServer());
            banButton.addActionListener(event -> toggleSelectedUserBan());
            stopButton.setEnabled(false);
            usersTable.setAutoCreateRowSorter(true);
            messagesTable.setAutoCreateRowSorter(true);
            styleTable(usersTable);
            styleTable(messagesTable);
            usersTable.getSelectionModel().addListSelectionListener(this::userSelectionChanged);
            refreshTimer = new Timer(500, event -> refreshStatus());
            refreshTimer.start();

            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent event) {
                    stopServer();
                    refreshTimer.stop();
                }
            });
            pack();
            setLocationRelativeTo(null);
            appendLog("Đăng nhập host thành công.");
            appendLog("Tài khoản host được cấu hình riêng, không dùng tài khoản chat.");
            reloadUsers();
            reloadMessages();
        }

        private static DefaultTableModel createModel(String... columns) {
            return new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        }

        private void styleTable(JTable table) {
            table.setFillsViewportHeight(true);
            table.setRowHeight(24);
            table.setBackground(INPUT);
            table.setForeground(TEXT);
            table.setGridColor(BORDER);
            table.setSelectionBackground(new Color(58, 91, 64));
            table.setSelectionForeground(TEXT);
            table.getTableHeader().setBackground(SURFACE);
            table.getTableHeader().setForeground(ACCENT);
        }

        private JTabbedPane createManagementTabs() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Users", createUsersPanel());
            tabs.addTab("Tin nhắn", createMessagesPanel());
            tabs.addTab("Nhật ký", createLogArea());
            return tabs;
        }

        private JPanel createUsersPanel() {
            JPanel panel = createDataPanel();
            JPanel toolbar = new JPanel();
            toolbar.setBackground(SURFACE);
            JButton refresh = createButton("Làm mới", false);
            refresh.addActionListener(event -> reloadUsers());
            toolbar.add(usersCountLabel);
            usersCountLabel.setForeground(ACCENT);
            toolbar.add(refresh);
            toolbar.add(banButton);
            panel.add(toolbar, BorderLayout.NORTH);
            panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
            return panel;
        }

        private JPanel createMessagesPanel() {
            JPanel panel = createDataPanel();
            JPanel toolbar = new JPanel();
            toolbar.setBackground(SURFACE);
            JButton refresh = createButton("Làm mới", false);
            JButton delete = createButton("Xóa tin đã chọn", false);
            refresh.addActionListener(event -> reloadMessages());
            delete.addActionListener(event -> deleteSelectedMessage());
            toolbar.add(messagesCountLabel);
            messagesCountLabel.setForeground(ACCENT);
            toolbar.add(refresh);
            toolbar.add(delete);
            panel.add(toolbar, BorderLayout.NORTH);
            panel.add(new JScrollPane(messagesTable), BorderLayout.CENTER);
            return panel;
        }

        private JPanel createDataPanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBackground(SURFACE);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
            return panel;
        }

        private void reloadUsers() {
            new SwingWorker<List<User>, Void>() {
                @Override
                protected List<User> doInBackground() throws Exception {
                    return userDAO.findAll();
                }

                @Override
                protected void done() {
                    try {
                        List<User> users = get();
                        usersModel.setRowCount(0);
                        for (User user : users) {
                            usersModel.addRow(new Object[]{
                                    user.getUserId(), user.getUsername(), user.getFullname(),
                                    user.getCreatedAt() == null ? "" : user.getCreatedAt().toString(),
                                    user.isBanned() ? "Bị ban" : "Hoạt động"
                            });
                        }
                        usersCountLabel.setText("Tổng users: " + users.size());
                        updateBanButtonLabel();
                    } catch (Exception e) {
                        showDatabaseError(e);
                    }
                }
            }.execute();
        }

        private void reloadMessages() {
            new SwingWorker<List<Message>, Void>() {
                @Override
                protected List<Message> doInBackground() throws Exception {
                    return messageDAO.findAll();
                }

                @Override
                protected void done() {
                    try {
                        List<Message> messages = get();
                        messagesModel.setRowCount(0);
                        for (Message message : messages) {
                            messagesModel.addRow(new Object[]{
                                    message.getMessageId(), message.getSenderId(),
                                    message.getSenderFullname(), message.getContent(),
                                    message.getSentAt() == null ? "" : message.getSentAt().toString()
                            });
                        }
                        messagesCountLabel.setText("Tổng tin nhắn: " + messages.size());
                    } catch (Exception e) {
                        showDatabaseError(e);
                    }
                }
            }.execute();
        }

        private void toggleSelectedUserBan() {
            int viewRow = usersTable.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Chọn một user trước.",
                        "Chưa chọn user", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int row = usersTable.convertRowIndexToModel(viewRow);
            int userId = ((Number) usersModel.getValueAt(row, 0)).intValue();
            String username = String.valueOf(usersModel.getValueAt(row, 1));
            boolean currentlyBanned = "Bị ban".equals(usersModel.getValueAt(row, 4));
            boolean newBannedState = !currentlyBanned;
            String action = newBannedState ? "ban" : "gỡ ban";
            int choice = JOptionPane.showConfirmDialog(this,
                    "Bạn muốn " + action + " user " + username + "?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }

            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    return userDAO.setBanned(userId, newBannedState);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            appendLog("Đã " + action + " user " + username + ".");
                            if (newBannedState) {
                                ClientHandler.disconnectBannedUser(userId);
                            }
                        }
                        reloadUsers();
                    } catch (Exception e) {
                        showDatabaseError(e);
                    }
                }
            }.execute();
        }

        private void deleteSelectedMessage() {
            int viewRow = messagesTable.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Chọn một tin nhắn trước.",
                        "Chưa chọn tin nhắn", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int row = messagesTable.convertRowIndexToModel(viewRow);
            int messageId = ((Number) messagesModel.getValueAt(row, 0)).intValue();
            int choice = JOptionPane.showConfirmDialog(this,
                    "Xóa tin nhắn đã chọn khỏi database?",
                    "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }

            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    return messageDAO.deleteById(messageId);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            appendLog("Đã xóa tin nhắn #" + messageId + ".");
                            ClientHandler.broadcastMessageDeleted(messageId);
                        }
                        reloadMessages();
                    } catch (Exception e) {
                        showDatabaseError(e);
                    }
                }
            }.execute();
        }

        private void userSelectionChanged(ListSelectionEvent event) {
            if (!event.getValueIsAdjusting()) {
                updateBanButtonLabel();
            }
        }

        private void updateBanButtonLabel() {
            int viewRow = usersTable.getSelectedRow();
            if (viewRow < 0) {
                banButton.setText("Ban user");
                return;
            }
            int row = usersTable.convertRowIndexToModel(viewRow);
            boolean banned = "Bị ban".equals(usersModel.getValueAt(row, 4));
            banButton.setText(banned ? "Gỡ ban user" : "Ban user");
        }

        private void showDatabaseError(Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            JOptionPane.showMessageDialog(this,
                    "Không thể đọc/cập nhật database: " + cause.getMessage(),
                    "Lỗi database", JOptionPane.ERROR_MESSAGE);
        }

        private JPanel createHeader() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(SURFACE);
            panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));

            JLabel title = new JLabel("Bảng điều khiển Chat Server");
            title.setForeground(ACCENT);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            panel.add(title, BorderLayout.NORTH);

            JPanel details = new JPanel(new GridBagLayout());
            details.setBackground(SURFACE);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(8, 0, 0, 18);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.gridx = 0;
            constraints.gridy = 0;
            JLabel statusTitle = new JLabel("Trạng thái:");
            statusTitle.setForeground(TEXT);
            details.add(statusTitle, constraints);
            constraints.gridx = 1;
            statusLabel.setForeground(ACCENT);
            details.add(statusLabel, constraints);
            constraints.gridx = 2;
            JLabel clientsTitle = new JLabel("Client đang chat:");
            clientsTitle.setForeground(TEXT);
            details.add(clientsTitle, constraints);
            constraints.gridx = 3;
            clientCountLabel.setForeground(ACCENT);
            details.add(clientCountLabel, constraints);
            panel.add(details, BorderLayout.SOUTH);
            return panel;
        }

        private JScrollPane createLogArea() {
            logArea.setEditable(false);
            logArea.setBackground(INPUT);
            logArea.setForeground(TEXT);
            logArea.setCaretColor(ACCENT);
            logArea.setLineWrap(true);
            logArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(logArea);
            scrollPane.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(BORDER), "Nhật ký hoạt động"));
            scrollPane.getViewport().setBackground(INPUT);
            return scrollPane;
        }

        private JPanel createControls() {
            JPanel panel = new JPanel(new BorderLayout(12, 0));
            panel.setBackground(BACKGROUND);
            panel.setBorder(BorderFactory.createEmptyBorder(0, 20, 18, 20));

            JPanel portPanel = new JPanel();
            portPanel.setBackground(BACKGROUND);
            JLabel portLabel = new JLabel("Cổng:");
            portLabel.setForeground(TEXT);
            portPanel.add(portLabel);
            portField.setBackground(INPUT);
            portField.setForeground(TEXT);
            portField.setCaretColor(ACCENT);
            portField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8)));
            portPanel.add(portField);
            panel.add(portPanel, BorderLayout.WEST);

            JPanel buttons = new JPanel();
            buttons.setBackground(BACKGROUND);
            buttons.add(startButton);
            buttons.add(stopButton);
            panel.add(buttons, BorderLayout.EAST);
            return panel;
        }

        private void startServer() {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                ChatServer candidate = new ChatServer(port);
                setHostLogger(this::appendLog);
                candidate.startAsync();
                server = candidate;
                portField.setEnabled(false);
                appendLog("Đang khởi động server ở cổng " + port + "...");
                refreshStatus();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Cổng phải là một số nguyên.",
                        "Cổng không hợp lệ", JOptionPane.WARNING_MESSAGE);
            } catch (IllegalArgumentException | IllegalStateException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(),
                        "Không thể khởi động", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void stopServer() {
            if (server != null) {
                server.stopServer();
                appendLog("Đã gửi yêu cầu dừng server.");
            }
            refreshStatus();
        }

        private void refreshStatus() {
            boolean active = server != null && (server.isRunning() || server.isStarting());
            statusLabel.setText(active
                    ? (server.isRunning() ? "Đang chạy" : "Đang khởi động") : "Đã dừng");
            clientCountLabel.setText(Integer.toString(ClientHandler.getConnectedClientCount()));
            startButton.setEnabled(!active && (server == null || !server.isThreadAlive()));
            stopButton.setEnabled(active);
            portField.setEnabled(!active && (server == null || !server.isThreadAlive()));
        }

        private void appendLog(String message) {
            SwingUtilities.invokeLater(() -> {
                logArea.append("[" + java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        + "] " + message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        private static JButton createButton(String text, boolean primary) {
            JButton button = new JButton(text);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBackground(primary ? ACCENT : INPUT);
            button.setForeground(primary ? BACKGROUND : TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(primary ? ACCENT : BORDER),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)));
            return button;
        }
    }
}
