package com.chatapp.server;

import com.chatapp.dao.MessageDAO;
import com.chatapp.model.Message;
import com.chatapp.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.Optional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles line-based requests from one connected client.
 *
 * Requests are REGISTER|base64(username)|base64(password)|base64(fullname),
 * LOGIN|base64(username)|base64(password), CHAT|base64(content),
 * SEARCH|base64(query), or EDIT|messageId|base64(content). Each request
 * ends with a newline. Authenticated clients receive MESSAGE events on this socket.
 */
public class ClientHandler implements Runnable {

    private static final int MAX_REQUEST_LENGTH = 8_192;
    private static final int HISTORY_LIMIT = 50;
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<ClientHandler> CONNECTED_CLIENTS = ConcurrentHashMap.newKeySet();

    private final Socket clientSocket;
    private final AuthService authService;
    private final MessageDAO messageDAO;
    private volatile User authenticatedUser;
    private volatile PrintWriter clientOutput;

    public ClientHandler(Socket clientSocket) {
        this(clientSocket, new AuthService(), new MessageDAO());
    }

    ClientHandler(Socket clientSocket, AuthService authService, MessageDAO messageDAO) {
        this.clientSocket = clientSocket;
        this.authService = authService;
        this.messageDAO = messageDAO;
    }

    public static int getConnectedClientCount() {
        return CONNECTED_CLIENTS.size();
    }

    @Override
    public void run() {
        String remoteAddress = String.valueOf(clientSocket.getRemoteSocketAddress());

        try (Socket socket = clientSocket;
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            clientOutput = output;
            ChatServer.log("Client đã kết nối: " + remoteAddress);
            String request;
            while ((request = input.readLine()) != null) {
                if (request.length() > MAX_REQUEST_LENGTH) {
                    sendError(output, "Yeu cau qua dai.");
                    break;
                }

                try {
                    handleRequest(request, output);
                } catch (IllegalArgumentException e) {
                    sendError(output, e.getMessage() == null
                            ? "Yeu cau khong hop le." : e.getMessage());
                } catch (SQLException e) {
                    ChatServer.log("Lỗi database khi xử lý client " + remoteAddress + ".");
                    System.err.println("Loi database khi xu ly client " + remoteAddress);
                    e.printStackTrace();
                    sendError(output, "Loi database. Vui long thu lai sau.");
                }
            }
        } catch (IOException e) {
            ChatServer.log("Mất kết nối với client " + remoteAddress + ": " + e.getMessage());
        } finally {
            CONNECTED_CLIENTS.remove(this);
            authenticatedUser = null;
            clientOutput = null;
            ChatServer.log("Client đã ngắt kết nối: " + remoteAddress);
        }
    }

    private void handleRequest(String request, PrintWriter output) throws SQLException {
        String[] parts = request.split("\\|", -1);

        switch (parts[0]) {
            case "REGISTER" -> register(parts, output);
            case "LOGIN" -> login(parts, output);
            case "CHAT" -> chat(parts, output);
            case "SEARCH" -> search(parts, output);
            case "EDIT" -> edit(parts, output);
            default -> sendError(output, "Lenh khong duoc ho tro.");
        }
    }

    private void register(String[] parts, PrintWriter output) throws SQLException {
        requireNotAuthenticated();
        requirePartCount(parts, 4);
        String username = decode(parts[1]);
        char[] password = decode(parts[2]).toCharArray();
        String fullname = decode(parts[3]);

        try {
            User user = authService.register(username, password, fullname);
            sendLine(output, "OK|REGISTERED|" + user.getUserId() + "|"
                    + encode(user.getUsername()) + "|" + encode(user.getFullname()));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void login(String[] parts, PrintWriter output) throws SQLException {
        requireNotAuthenticated();
        requirePartCount(parts, 3);
        String username = decode(parts[1]);
        char[] password = decode(parts[2]).toCharArray();
        try {
            Optional<User> result = authService.login(username, password);
            if (result.isEmpty()) {
                sendError(output, "Ten dang nhap hoac mat khau khong dung.");
                return;
            }

            User user = result.get();
            authenticatedUser = user;
            synchronized (output) {
                sendLine(output, "OK|LOGIN|" + user.getUserId() + "|"
                        + encode(user.getUsername()) + "|" + encode(user.getFullname()));
                CONNECTED_CLIENTS.add(this);
                sendHistory(output);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void chat(String[] parts, PrintWriter output) throws SQLException {
        requirePartCount(parts, 2);
        User sender = authenticatedUser;
        if (sender == null) {
            throw new IllegalArgumentException("Hay dang nhap truoc khi gui tin nhan.");
        }

        String content = decode(parts[1]);
        if (content.isBlank()) {
            throw new IllegalArgumentException("Tin nhan khong duoc de trong.");
        }

        int messageId = messageDAO.create(sender.getUserId(), content);
        ChatServer.log("Tin nhắn mới từ " + sender.getFullname() + ".");
        String event = "MESSAGE|" + messageId + "|" + sender.getUserId() + "|"
                + encode(sender.getFullname()) + "|" + encode(content) + "|"
                + LocalTime.now().format(MESSAGE_TIME_FORMAT);
        broadcast(event);
    }

    private void search(String[] parts, PrintWriter output) throws SQLException {
        requireAuthenticated();
        requirePartCount(parts, 2);
        String query = decode(parts[1]).trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Nhap noi dung can tim.");
        }

        List<Message> matches = messageDAO.search(query);
        synchronized (output) {
            for (Message message : matches) {
                sendLine(output, "SEARCH_RESULT|" + message.getMessageId() + "|"
                        + message.getSenderId() + "|" + encode(message.getSenderFullname()) + "|"
                        + encode(message.getContent()) + "|"
                        + message.getSentAt().format(HISTORY_TIME_FORMAT));
            }
            sendLine(output, "SEARCH_END|" + matches.size());
        }
    }

    private void edit(String[] parts, PrintWriter output) throws SQLException {
        requireAuthenticated();
        requirePartCount(parts, 3);
        User sender = authenticatedUser;

        int messageId;
        try {
            messageId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ma tin nhan khong hop le.");
        }

        String content = decode(parts[2]).trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Tin nhan khong duoc de trong.");
        }
        if (!messageDAO.updateContent(messageId, sender.getUserId(), content)) {
            sendError(output, "Ban chi co the sua tin nhan cua chinh minh.");
            return;
        }

        ChatServer.log(sender.getFullname() + " đã sửa tin nhắn #" + messageId + ".");
        broadcast("MESSAGE_UPDATED|" + messageId + "|" + sender.getUserId() + "|"
                + encode(sender.getFullname()) + "|" + encode(content) + "|"
                + LocalTime.now().format(MESSAGE_TIME_FORMAT));
    }

    private void requireAuthenticated() {
        if (authenticatedUser == null) {
            throw new IllegalArgumentException("Hay dang nhap truoc khi thuc hien thao tac nay.");
        }
    }

    public static void broadcastMessageDeleted(int messageId) {
        String event = "MESSAGE_DELETED|" + messageId;
        for (ClientHandler handler : CONNECTED_CLIENTS) {
            handler.sendLine(event);
        }
    }

    public static void disconnectBannedUser(int userId) {
        for (ClientHandler handler : CONNECTED_CLIENTS) {
            User user = handler.authenticatedUser;
            if (user != null && user.getUserId() == userId) {
                handler.sendLine("ACCOUNT_BANNED");
                try {
                    handler.clientSocket.close();
                } catch (IOException ignored) {
                    // The connection is already closing.
                }
            }
        }
    }

    private void sendHistory(PrintWriter output) throws SQLException {
        List<Message> messages = messageDAO.findRecent(HISTORY_LIMIT);
        for (Message message : messages) {
            sendLine(output, "HISTORY|" + message.getMessageId() + "|"
                    + message.getSenderId() + "|" + encode(message.getSenderFullname()) + "|"
                    + encode(message.getContent()) + "|"
                    + message.getSentAt().format(HISTORY_TIME_FORMAT));
        }
        sendLine(output, "HISTORY_END|" + messages.size());
    }

    private void requireNotAuthenticated() {
        if (authenticatedUser != null) {
            throw new IllegalArgumentException("Ket noi nay da dang nhap roi.");
        }
    }

    private void broadcast(String message) {
        for (ClientHandler handler : CONNECTED_CLIENTS) {
            handler.sendLine(message);
        }
    }

    private void sendLine(String message) {
        PrintWriter output = clientOutput;
        if (output != null) {
            sendLine(output, message);
        }
    }

    private void sendLine(PrintWriter output, String message) {
        synchronized (output) {
            output.println(message);
            if (output.checkError()) {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                    // The connection is already unavailable.
                }
            }
        }
    }

    private void requirePartCount(String[] parts, int expectedCount) {
        if (parts.length != expectedCount) {
            throw new IllegalArgumentException("So truong trong yeu cau khong hop le.");
        }
    }

    private String decode(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(PrintWriter output, String message) {
        sendLine(output, "ERR|" + encode(message));
    }
}
