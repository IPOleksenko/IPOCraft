package dev.IPOleksenko.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.scene.control.ListView;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class UserManager {

    public static class UserEntry {
        public String username;
        public String uuid;
        public String accessToken;
        public String authSession;

        public UserEntry(String username, String uuid, String accessToken, String authSession) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.authSession = authSession;
        }
    }

    private List<UserEntry> users = new ArrayList<>();
    private final ListView<String> listView;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path savePath = Paths.get(System.getProperty("user.home"), "IPOCraft", "users.json");

    public UserManager(ListView<String> listView) {
        this.listView = listView;
        loadUsers();
    }

    public void addUser(String username, String uuid, String accessToken, String authSession) {
        if (isUserExists(username)) {
            System.out.println("User with this name already exists.");
            return;
        }

        // Use the provided UUID if it's not empty, otherwise generate a new one
        String newUuid = (uuid != null && !uuid.isEmpty()) ? uuid : generateUuid();
        String newAccessToken = (accessToken != null && !accessToken.isEmpty()) ? accessToken : null;
        String newAuthSession = (authSession != null && !authSession.isEmpty()) ? authSession : null;

        UserEntry user = new UserEntry(username, newUuid, newAccessToken, newAuthSession);
        users.add(user);
        listView.getItems().add(formatDisplayName(user));
        saveUsers();
    }

    public void deleteSelectedUser() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            users.remove(idx);
            listView.getItems().remove(idx);
            saveUsers();
        }
    }

    public boolean isUserExists(String username) {
        return users.stream()
                .anyMatch(u -> Objects.equals(u.username, username));
    }

    public void loadUsers() {
        try {
            listView.getItems().clear();

            if (Files.exists(savePath)) {
                String json = Files.readString(savePath);
                Type listType = new TypeToken<List<UserEntry>>() {}.getType();
                users = gson.fromJson(json, listType);

                boolean changed = false;
                for (UserEntry u : users) {
                    if (u.uuid == null || u.uuid.isEmpty()) {
                        u.uuid = generateUuid();
                        changed = true;
                    }
                    if (u.accessToken == null || u.accessToken.isEmpty()) {
                        u.accessToken = "0";
                        changed = true;
                    }
                    if (u.authSession == null || u.authSession.isEmpty()) {
                        u.authSession = "0";
                        changed = true;
                    }
                    listView.getItems().add(formatDisplayName(u));
                }
                if (changed) saveUsers();
            }
        } catch (IOException e) {
            System.err.println("Failed to load users: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try {
            Files.createDirectories(savePath.getParent());
            String json = gson.toJson(users);
            Files.writeString(savePath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save users: " + e.getMessage());
        }
    }

    private String formatDisplayName(UserEntry user) {
        return user.username != null ? user.username : "";
    }

    public List<UserEntry> getUsers() {
        return users;
    }

    public void updateUser(int index, UserEntry user) {
        UserEntry old = users.get(index);
        user.uuid = old.uuid;
        user.accessToken = (user.accessToken == null || user.accessToken.isEmpty()) ? old.accessToken : user.accessToken;
        user.authSession = (user.authSession == null || user.authSession.isEmpty()) ? old.authSession : user.authSession;
        users.set(index, user);
        listView.getItems().set(index, formatDisplayName(user));
        saveUsers();
    }

    // Generate UUID (if not provided)
    private String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
