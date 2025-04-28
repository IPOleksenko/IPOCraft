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
        public boolean minecraftAccount;
        public String login;
        public String password;
        public String uuid;

        public UserEntry(String username, boolean minecraftAccount, String login, String password, String uuid) {
            this.username = username;
            this.minecraftAccount = minecraftAccount;
            this.login = login;
            this.password = password;
            this.uuid = uuid;
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

    public void addUser(String username, boolean minecraftAccount) {
        addUser(username, minecraftAccount, null, null);
    }

    public void addUser(String username, boolean minecraftAccount, String login, String password) {
        if (isUserExists(username, minecraftAccount)) {
            System.out.println("User with this name already exists.");
            return;
        }

        String newUuid = UUID.randomUUID().toString();
        UserEntry user = new UserEntry(username, minecraftAccount, login, password, newUuid);
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

    public boolean isUserExists(String username, boolean minecraftAccount) {
        return users.stream()
                .anyMatch(u -> Objects.equals(u.username, username) && u.minecraftAccount == minecraftAccount);
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
                        u.uuid = UUID.randomUUID().toString();
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
        String displayName = (user.minecraftAccount ?
                (user.login != null ? user.login : "") :
                (user.username != null ? user.username : ""));
        return displayName + (user.minecraftAccount ? " (Account)" : " (No Account)");
    }

    public List<UserEntry> getUsers() {
        return users;
    }

    public void updateUser(int index, UserEntry user) {
        UserEntry old = users.get(index);
        user.uuid = old.uuid;
        users.set(index, user);
        listView.getItems().set(index, formatDisplayName(user));
        saveUsers();
    }
}
