package com.chatapp.dao;

import com.chatapp.database.DatabaseConnection;
import com.chatapp.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    /** Saves a message and returns its generated message ID. */
    public int create(int senderId, String content) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, content) VALUES (?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, senderId);
            statement.setString(2, content);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Luu tin nhan thanh cong nhung khong lay duoc message_id.");
    }

    /** Returns the newest messages, ordered from oldest to newest for display. */
    public List<Message> findRecent(int limit) throws SQLException {
        if (limit < 1) {
            throw new IllegalArgumentException("limit phai lon hon 0.");
        }

        String sql = "SELECT recent_messages.message_id, recent_messages.sender_id, "
                + "recent_messages.content, recent_messages.sent_at, users.fullname AS sender_fullname "
                + "FROM (SELECT message_id, sender_id, content, sent_at FROM messages "
                + "ORDER BY sent_at DESC, message_id DESC LIMIT ?"
                + ") AS recent_messages JOIN users ON users.user_id = recent_messages.sender_id "
                + "ORDER BY recent_messages.sent_at ASC, recent_messages.message_id ASC";
        List<Message> messages = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Message message = new Message(
                            resultSet.getInt("message_id"),
                            resultSet.getInt("sender_id"),
                            resultSet.getString("content"),
                            resultSet.getTimestamp("sent_at").toLocalDateTime()
                    );
                    message.setSenderFullname(resultSet.getString("sender_fullname"));
                    messages.add(message);
                }
            }
        }

        return messages;
    }

    /** Returns all messages with their sender's full name for host administration. */
    public List<Message> findAll() throws SQLException {
        String sql = "SELECT messages.message_id, messages.sender_id, messages.content, "
                + "messages.sent_at, users.fullname AS sender_fullname "
                + "FROM messages JOIN users ON users.user_id = messages.sender_id "
                + "ORDER BY messages.sent_at DESC, messages.message_id DESC";
        List<Message> messages = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Message message = new Message(
                        resultSet.getInt("message_id"),
                        resultSet.getInt("sender_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("sent_at").toLocalDateTime()
                );
                message.setSenderFullname(resultSet.getString("sender_fullname"));
                messages.add(message);
            }
        }

        return messages;
    }

    public List<Message> search(String query) throws SQLException {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String sql = "SELECT messages.message_id, messages.sender_id, messages.content, "
                + "messages.sent_at, users.fullname AS sender_fullname "
                + "FROM messages JOIN users ON users.user_id = messages.sender_id "
                + "WHERE messages.content LIKE ? "
                + "ORDER BY messages.sent_at DESC, messages.message_id DESC";
        List<Message> messages = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + query.trim() + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Message message = new Message(
                            resultSet.getInt("message_id"),
                            resultSet.getInt("sender_id"),
                            resultSet.getString("content"),
                            resultSet.getTimestamp("sent_at").toLocalDateTime()
                    );
                    message.setSenderFullname(resultSet.getString("sender_fullname"));
                    messages.add(message);
                }
            }
        }

        return messages;
    }

    public boolean updateContent(int messageId, int senderId, String content) throws SQLException {
        String sql = "UPDATE messages SET content = ? WHERE message_id = ? AND sender_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, content);
            statement.setInt(2, messageId);
            statement.setInt(3, senderId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteById(int messageId) throws SQLException {
        String sql = "DELETE FROM messages WHERE message_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, messageId);
            return statement.executeUpdate() > 0;
        }
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DatabaseConnection.getConnection();
        if (connection == null) {
            throw new SQLException("Khong the ket noi den database chat_online.");
        }
        return connection;
    }
}
