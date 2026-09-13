package com.IPOleksenko.auth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MicrosoftAuthService {
    private static final String SCOPE = "XboxLive.signin offline_access";

    public static String getClientId() {
        try {
            return com.IPOleksenko.config.ConfigManager.getInstance().getConfig().getMicrosoftClientId();
        } catch (Exception e) {
            return "43b56eb6-cbec-4278-9c39-d70c21aa6d49";
        }
    }

    public static class DeviceCodeResponse {
        public final String userCode;
        public final String deviceCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;

        public DeviceCodeResponse(String userCode, String deviceCode, String verificationUri, int expiresIn, int interval) {
            this.userCode = userCode;
            this.deviceCode = deviceCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    public static DeviceCodeResponse requestDeviceCode() throws IOException {
        URL url = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
        String body = "client_id=" + URLEncoder.encode(getClientId(), StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

        String response = postForm(url, body);
        JSONObject json = new JSONObject(response);

        return new DeviceCodeResponse(
                json.getString("user_code"),
                json.getString("device_code"),
                json.getString("verification_uri"),
                json.getInt("expires_in"),
                json.optInt("interval", 5)
        );
    }

    public static Account pollAndAuthenticate(DeviceCodeResponse dc, AtomicBoolean cancelFlag, Consumer<String> statusConsumer) throws Exception {
        long deadline = System.currentTimeMillis() + (dc.expiresIn * 1000L);
        int intervalMs = Math.max(2, dc.interval) * 1000;

        String msAccessToken = null;
        String msRefreshToken = null;

        while (System.currentTimeMillis() < deadline) {
            if (cancelFlag != null && cancelFlag.get()) {
                throw new InterruptedException("Authentication cancelled by user.");
            }

            try {
                URL tokenUrl = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
                String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                        + "&client_id=" + URLEncoder.encode(getClientId(), StandardCharsets.UTF_8)
                        + "&device_code=" + URLEncoder.encode(dc.deviceCode, StandardCharsets.UTF_8);

                String response = postForm(tokenUrl, body);
                JSONObject json = new JSONObject(response);

                if (json.has("access_token")) {
                    msAccessToken = json.getString("access_token");
                    msRefreshToken = json.optString("refresh_token", null);
                    break;
                }
            } catch (IOException e) {
                // Check if it's pending
                String err = e.getMessage();
                if (err != null && err.contains("authorization_pending")) {
                    if (statusConsumer != null) {
                        statusConsumer.accept("Waiting for user to enter code in browser...");
                    }
                } else if (err != null && err.contains("slow_down")) {
                    intervalMs += 2000;
                } else if (err != null && (err.contains("expired_token") || err.contains("authorization_declined"))) {
                    throw new IOException("Authorization expired or declined.");
                }
            }

            Thread.sleep(intervalMs);
        }

        if (msAccessToken == null) {
            throw new IOException("Failed to obtain Microsoft access token (timeout).");
        }

        if (statusConsumer != null) statusConsumer.accept("Authenticating with Xbox Live...");
        return completeMinecraftAuthentication(msAccessToken, msRefreshToken, statusConsumer);
    }

    public static Account refreshAccount(Account account) throws Exception {
        if (!account.isMicrosoft() || account.getRefreshToken() == null) {
            return account;
        }

        URL tokenUrl = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
        String body = "grant_type=refresh_token"
                + "&client_id=" + URLEncoder.encode(getClientId(), StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(account.getRefreshToken(), StandardCharsets.UTF_8);

        String response = postForm(tokenUrl, body);
        JSONObject json = new JSONObject(response);

        String newMsToken = json.getString("access_token");
        String newRefreshToken = json.optString("refresh_token", account.getRefreshToken());

        return completeMinecraftAuthentication(newMsToken, newRefreshToken, null);
    }

    private static Account completeMinecraftAuthentication(String msAccessToken, String msRefreshToken, Consumer<String> statusConsumer) throws Exception {
        // Step 1: Xbox Live authentication
        if (statusConsumer != null) statusConsumer.accept("Logging into Xbox Live...");
        JSONObject xblAuthReq = new JSONObject();
        JSONObject xblProps = new JSONObject();
        xblProps.put("AuthMethod", "RPS");
        xblProps.put("SiteName", "user.auth.xboxlive.com");
        xblProps.put("RpsTicket", "d=" + msAccessToken);
        xblAuthReq.put("Properties", xblProps);
        xblAuthReq.put("RelyingParty", "http://auth.xboxlive.com");
        xblAuthReq.put("TokenType", "JWT");

        String xblResp = postJson(new URL("https://user.auth.xboxlive.com/user/authenticate"), xblAuthReq.toString());
        JSONObject xblJson = new JSONObject(xblResp);
        String xblToken = xblJson.getString("Token");
        String uhs = xblJson.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");

        // Step 2: XSTS Token
        if (statusConsumer != null) statusConsumer.accept("Requesting XSTS authorization...");
        JSONObject xstsReq = new JSONObject();
        JSONObject xstsProps = new JSONObject();
        xstsProps.put("SandboxId", "RETAIL");
        JSONArray userTokens = new JSONArray();
        userTokens.put(xblToken);
        xstsProps.put("UserTokens", userTokens);
        xstsReq.put("Properties", xstsProps);
        xstsReq.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.put("TokenType", "JWT");

        String xstsResp = postJson(new URL("https://xsts.auth.xboxlive.com/xsts/authorize"), xstsReq.toString());
        JSONObject xstsJson = new JSONObject(xstsResp);
        String xstsToken = xstsJson.getString("Token");

        // Step 3: Minecraft Login
        if (statusConsumer != null) statusConsumer.accept("Authenticating with Minecraft Services...");
        JSONObject mcLoginReq = new JSONObject();
        mcLoginReq.put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        String mcLoginResp;
        try {
            mcLoginResp = postJson(new URL("https://api.minecraftservices.com/authentication/login_with_xbox"), mcLoginReq.toString());
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Invalid app registration")) {
                throw new IOException("Mojang rejected the Azure Client ID (Invalid app registration).\n" +
                        "Please choose an approved preset in Launcher Settings -> Microsoft Azure Client ID,\n" +
                        "or submit your custom Azure App for review at https://aka.ms/mce-reviewappid.");
            }
            throw e;
        }
        JSONObject mcLoginJson = new JSONObject(mcLoginResp);
        String mcAccessToken = mcLoginJson.getString("access_token");
        long expiresInSec = mcLoginJson.optLong("expires_in", 86400);
        long expiresAt = System.currentTimeMillis() + (expiresInSec * 1000L);

        // Step 4: Minecraft Profile
        if (statusConsumer != null) statusConsumer.accept("Fetching Minecraft profile...");
        String profileResp;
        try {
            profileResp = getWithAuth(new URL("https://api.minecraftservices.com/minecraft/profile"), mcAccessToken);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new IOException("No Minecraft Java profile found on this Microsoft account.\nPlease make sure you own Minecraft: Java Edition and have set up your player name at https://minecraft.net.");
            }
            throw e;
        }

        JSONObject profileJson = new JSONObject(profileResp);

        String uuid = profileJson.getString("id");
        // format UUID with hyphens if needed
        if (uuid.length() == 32) {
            uuid = uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        }
        String name = profileJson.getString("name");
        String skinUrl = null;
        if (profileJson.has("skins")) {
            JSONArray skins = profileJson.getJSONArray("skins");
            if (skins.length() > 0) {
                skinUrl = skins.getJSONObject(0).optString("url", null);
            }
        }

        return new Account(name, uuid, "MICROSOFT", mcAccessToken, "0", msRefreshToken, expiresAt, skinUrl);
    }

    private static String postForm(URL url, String formBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + code);

        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) {
            if (resp.contains("AADSTS70002")) {
                throw new IOException("Azure configuration error: The application must have public/mobile client flows enabled.\nIn Azure Portal -> App registrations -> Authentication -> Set 'Allow public client flows' to 'Yes'.");
            }
            throw new IOException("HTTP " + code + ": " + resp);
        }
        return resp;
    }

    private static String postJson(URL url, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + code);

        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) {
            if (resp.contains("XErr")) {
                try {
                    JSONObject errObj = new JSONObject(resp);
                    long xerr = errObj.optLong("XErr", 0);
                    if (xerr == 2148916233L) {
                        throw new IOException("This Microsoft account does not have an Xbox profile. Please visit https://xbox.com to set one up.");
                    } else if (xerr == 2148916235L) {
                        throw new IOException("Xbox Live is not available in your country or region.");
                    } else if (xerr == 2148916236L || xerr == 2148916237L) {
                        throw new IOException("Adult verification required. Please verify your age at https://xbox.com.");
                    } else if (xerr == 2148916238L) {
                        throw new IOException("This is a child account and must be added to a Microsoft Family by an adult organizer.");
                    }
                } catch (IOException ioe) {
                    throw ioe;
                } catch (Exception ignored) {}
            }
            if (code == 403 && resp.contains("Invalid app registration")) {
                throw new IOException("HTTP 403: Invalid app registration (" + getClientId() + ").\n"
                        + "Mojang requires custom Azure Client IDs to be submitted for approval via https://aka.ms/mce-reviewappid.\n"
                        + "Ensure your app is approved by Mojang and 'Allow public client flows' is enabled in Azure Portal.");
            }
            throw new IOException("HTTP " + code + ": " + resp);
        }
        return resp;
    }

    private static String getWithAuth(URL url, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + code);

        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + resp);
        }
        return resp;
    }
}

