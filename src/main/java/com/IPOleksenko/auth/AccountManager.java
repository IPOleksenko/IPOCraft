package com.IPOleksenko.auth;

import com.IPOleksenko.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class AccountManager {
    private static final Path USERS_JSON = Paths.get(System.getProperty("user.home"), "IPOCraft", "users.json");
    private static AccountManager instance;

    private final List<Account> accounts = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private AccountManager() {
        loadAccounts();
    }

    public static synchronized AccountManager getInstance() {
        if (instance == null) {
            instance = new AccountManager();
        }
        return instance;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public Account getActiveAccount() {
        String activeUuid = ConfigManager.getInstance().getConfig().getActiveUserUuid();
        if (activeUuid != null && !activeUuid.isEmpty()) {
            for (Account acc : accounts) {
                if (activeUuid.equalsIgnoreCase(acc.getUuid())) {
                    return acc;
                }
            }
        }

        if (!accounts.isEmpty()) {
            Account first = accounts.get(0);
            setActiveAccount(first);
            return first;
        }
        return null;
    }

    public void setActiveAccount(Account account) {
        if (account != null) {
            ConfigManager.getInstance().getConfig().setActiveUserUuid(account.getUuid());
            ConfigManager.getInstance().saveConfig();
        }
    }

    public void addAccount(Account account) {
        if (account == null) return;
        // remove existing with same uuid or username
        accounts.removeIf(a -> a.getUuid().equalsIgnoreCase(account.getUuid()) ||
                (a.getUsername().equalsIgnoreCase(account.getUsername()) && a.getType().equalsIgnoreCase(account.getType())));
        accounts.add(account);
        saveAccounts();
        setActiveAccount(account);
    }

    public void removeAccount(Account account) {
        if (account == null) return;
        accounts.remove(account);
        saveAccounts();

        if (account.getUuid().equalsIgnoreCase(ConfigManager.getInstance().getConfig().getActiveUserUuid())) {
            if (!accounts.isEmpty()) {
                setActiveAccount(accounts.get(0));
            } else {
                ConfigManager.getInstance().getConfig().setActiveUserUuid("");
                ConfigManager.getInstance().saveConfig();
            }
        }
    }

    public void loadAccounts() {
        accounts.clear();
        try {
            if (Files.exists(USERS_JSON)) {
                String json = Files.readString(USERS_JSON);
                Type type = new TypeToken<List<Account>>() {}.getType();
                List<Account> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    accounts.addAll(loaded);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load users.json: " + e.getMessage());
        }
    }

    public void saveAccounts() {
        try {
            Files.createDirectories(USERS_JSON.getParent());
            String json = gson.toJson(accounts);
            Files.writeString(USERS_JSON, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save users.json: " + e.getMessage());
        }
    }

    public Account refreshActiveAccountIfNeeded() {
        Account active = getActiveAccount();
        if (active != null && active.isMicrosoft() && active.isExpired()) {
            try {
                Account refreshed = MicrosoftAuthService.refreshAccount(active);
                addAccount(refreshed);
                return refreshed;
            } catch (Exception e) {
                System.err.println("Failed to refresh Microsoft account token: " + e.getMessage());
            }
        }
        return active;
    }

    public void refreshAllAccounts() {
        loadAccounts();
        for (Account acc : new ArrayList<>(accounts)) {
            if (acc.isMicrosoft() && acc.getRefreshToken() != null) {
                try {
                    Account refreshed = MicrosoftAuthService.refreshAccount(acc);
                    addAccount(refreshed);
                } catch (Exception e) {
                    System.err.println("Failed to refresh account " + acc.getUsername() + ": " + e.getMessage());
                }
            }
        }
    }
}

