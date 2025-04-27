package dev.IPOleksenko.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.scene.control.ListView;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;

public class UserManager {

    public static class UserEntry {
        public String username;
        public boolean minecraftAccount;
        public String login;
        public String password;

        public UserEntry(String username, boolean minecraftAccount, String login, String password) {
            this.username = username;
            this.minecraftAccount = minecraftAccount;
            this.login = login;
            this.password = password;
        }
    }

    private List<UserEntry> users = new ArrayList<>();
    private final ListView<String> listView;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path savePath = Paths.get(System.getenv("APPDATA"), "IPOCraft", "users.json");

    public UserManager(ListView<String> listView) {
        this.listView = listView;
        loadUsers();
    }

    public void addUser(String username, boolean minecraftAccount) {
        addUser(username, minecraftAccount, null, null);
    }

    public void addUser(String username, boolean minecraftAccount, String login, String password) {
        UserEntry user = new UserEntry(username, minecraftAccount, login, password);
        users.add(user);
        listView.getItems().add(formatDisplayName(user));
        saveUsers();
    }

    public void deleteSelectedUser() {
        int selectedIndex = listView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            users.remove(selectedIndex);
            listView.getItems().remove(selectedIndex);
            saveUsers();
        }
    }

    public boolean isUserExists(String username, boolean minecraftAccount) {
        return users.stream()
                .anyMatch(u -> Objects.equals(u.username, username) && u.minecraftAccount == minecraftAccount);
    }

    private void loadUsers() {
        try {
            if (Files.exists(savePath)) {
                String json = new String(Files.readAllBytes(savePath));
                Type listType = new TypeToken<List<UserEntry>>() {}.getType();
                users = gson.fromJson(json, listType);
                for (UserEntry user : users) {
                    listView.getItems().add(formatDisplayName(user));
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load users: " + e.getMessage());
        }
    }

    private void saveUsers() {
        try {
            Files.createDirectories(savePath.getParent());
            String json = gson.toJson(users);
            Files.write(savePath, json.getBytes());
        } catch (IOException e) {
            System.out.println("Failed to save users: " + e.getMessage());
        }
    }

    private String formatDisplayName(UserEntry user) {
        String displayName = (user.username != null && !user.username.isEmpty()) ? user.username : user.login;
        return displayName + (user.minecraftAccount ? " (Account)" : " (No Account)");
    }

    public List<UserEntry> getUsers() {
        return users;
    }

    public void updateUser(int index, UserEntry user) {
        users.set(index, user);
        listView.getItems().set(index, formatDisplayName(user));
        saveUsers();
    }
}
