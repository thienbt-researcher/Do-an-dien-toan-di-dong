package com.chatapp.server;

import com.chatapp.dao.UserDAO;
import com.chatapp.model.User;
import com.chatapp.security.PasswordHasher;

import java.sql.SQLException;
import java.util.Optional;

/** Handles registration and login on the server side. */
public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User register(String username, char[] password, String fullname) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanFullname = fullname == null ? "" : fullname.trim();

        if (cleanUsername.isEmpty()) {
            throw new IllegalArgumentException("Ten dang nhap khong duoc de trong.");
        }
        if (cleanFullname.isEmpty()) {
            throw new IllegalArgumentException("Ho ten khong duoc de trong.");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Mat khau phai co it nhat 8 ky tu.");
        }
        if (userDAO.findByUsername(cleanUsername).isPresent()) {
            throw new IllegalArgumentException("Ten dang nhap da duoc su dung.");
        }

        User user = new User(0, cleanUsername, PasswordHasher.hash(password), cleanFullname);
        userDAO.create(user);
        return user;
    }

    /** Returns the account for valid credentials, or empty for invalid credentials. */
    public Optional<User> login(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || password == null) {
            return Optional.empty();
        }

        Optional<User> user = userDAO.findByUsername(username.trim());
        if (user.isPresent() && !user.get().isBanned()
                && PasswordHasher.verify(password, user.get().getPasswordHash())) {
            return user;
        }
        return Optional.empty();
    }
}
