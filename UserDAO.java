package com.chatapp.dao;

import com.chatapp.database.DatabaseConnection;
import com.chatapp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    /** Finds an account by its unique username. */
    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, fullname, is_banned "
                + "FROM users WHERE username = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User(
                            resultSet.getInt("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("fullname")
                    );
                    user.setBanned(resultSet.getBoolean("is_banned"));
                    return Optional.of(user);
                }
            }
        }

        return Optional.empty();
    }

    /** Inserts a new account and returns its generated user ID. */
    public int create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, fullname) VALUES (?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getFullname());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    user.setUserId(userId);
                    return userId;
                }
            }
        }

        throw new SQLException("Tao tai khoan thanh cong nhung khong lay duoc user_id.");
    }

    /** Returns user details for host administration, excluding password hashes. */
    public List<User> findAll() throws SQLException {
        String sql = "SELECT user_id, username, fullname, created_at, is_banned "
                + "FROM users ORDER BY user_id";
        List<User> users = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                User user = new User(
                        resultSet.getInt("user_id"),
                        resultSet.getString("username"),
                        null,
                        resultSet.getString("fullname")
                );
                java.sql.Timestamp createdAt = resultSet.getTimestamp("created_at");
                user.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                user.setBanned(resultSet.getBoolean("is_banned"));
                users.add(user);
            }
        }

        return users;
    }

    /** Changes whether an account may log in. */
    public boolean setBanned(int userId, boolean banned) throws SQLException {
        String sql = "UPDATE users SET is_banned = ? WHERE user_id = ?";
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, banned);
                statement.setInt(2, userId);
                return statement.executeUpdate() > 0;
            }
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
