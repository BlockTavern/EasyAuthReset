package xyz.nikitacartes.easyauthreset.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nikitacartes.easyauthreset.EasyAuthReset;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱持久化，两个文件：
 * <ul>
 *   <li>{@code easyauthreset_emails.json}（v2）：
 *       {@code emails}（最近一次使用/待绑定邮箱）+ {@code bindings}（玩家自助验证绑定，可信），
 *       读取旧版扁平格式时自动迁移；</li>
 *   <li>{@code easyauthreset_mailmap.json}：<b>服主预登记</b>邮箱（玩家名小写 或 UUID → 邮箱），
 *       启动时加载、运行时<b>从不写回</b>，优先级最高且玩家无法篡改。</li>
 * </ul>
 */
public class PlayerEmailStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);

    private static class Data {
        public String version = "2";
        public Map<String, String> emails = new HashMap<>();
        public Map<String, String> bindings = new HashMap<>();
    }

    private final Map<String, String> emailMap = new ConcurrentHashMap<>();
    private final Map<String, String> bindingMap = new ConcurrentHashMap<>();
    private final Map<String, String> ownerMap = new ConcurrentHashMap<>();
    private final Path filePath;
    private final Path ownerFilePath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PlayerEmailStorage() {
        this.filePath = FabricLoader.getInstance().getConfigDir().resolve("easyauthreset_emails.json");
        this.ownerFilePath = FabricLoader.getInstance().getConfigDir().resolve("easyauthreset_mailmap.json");
        load();
        loadOwnerMap();
    }

    /** 最近一次使用（或待绑定）的邮箱。 */
    public void setEmail(String uuid, String email) {
        emailMap.put(uuid, email);
        save();
    }

    public String getEmail(String uuid) {
        return emailMap.get(uuid);
    }

    /** 玩家自助验证绑定的邮箱。 */
    public void setBinding(String uuid, String email) {
        bindingMap.put(uuid, email);
        save();
    }

    public String getBinding(String uuid) {
        return bindingMap.get(uuid);
    }

    /** 服主预登记邮箱（键 = 玩家名小写 或 UUID）。 */
    public String getOwnerEmail(String key) {
        if (key == null) {
            return null;
        }
        return ownerMap.get(key);
    }

    public boolean hasOwnerEntries() {
        return !ownerMap.isEmpty();
    }

    /** 决定重置邮件收件地址：服主预登记 &gt; 玩家绑定。 */
    public String resolveEffectiveEmail(String uuid, String playerNameLowerCase) {
        String owner = getOwnerEmail(uuid);
        if (owner == null) {
            owner = getOwnerEmail(playerNameLowerCase);
        }
        return owner != null ? owner : getBinding(uuid);
    }

    // ---- 加载/保存 ----

    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("bindings")) {
                // v2 格式
                Data data = gson.fromJson(root, Data.class);
                if (data.emails != null) {
                    emailMap.putAll(data.emails);
                }
                if (data.bindings != null) {
                    bindingMap.putAll(data.bindings);
                }
            } else {
                // 旧版扁平格式：{"uuid": "email"} → emails
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                        emailMap.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read easyauthreset_emails.json", e);
        }
    }

    private void loadOwnerMap() {
        if (!Files.exists(ownerFilePath)) {
            // 首次运行：生成空模板并提示，避免服主不知道有该文件
            try (Writer writer = Files.newBufferedWriter(ownerFilePath)) {
                gson.toJson(Map.of(), writer);
                LOGGER.info("Created admin mailmap template config/easyauthreset_mailmap.json (format: {\"username_lowercase\": \"email\", \"player_uuid\": \"email\"})");
            } catch (IOException e) {
                LOGGER.error("Failed to write easyauthreset_mailmap.json", e);
            }
            return;
        }
        try (Reader reader = Files.newBufferedReader(ownerFilePath)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                    ownerMap.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read easyauthreset_mailmap.json (format: {\"username_lowercase\": \"email\"})", e);
        }
    }

    private void save() {
        Data data = new Data();
        data.emails.putAll(emailMap);
        data.bindings.putAll(bindingMap);
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write easyauthreset_emails.json", e);
        }
    }
}
