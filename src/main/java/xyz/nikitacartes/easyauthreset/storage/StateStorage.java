package xyz.nikitacartes.easyauthreset.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
 * 模组易失状态的内存 + 文件持久化（{@code config/easyauthreset_state.json}）：
 * <ul>
 *   <li>验证码记录（服务器重启后仍有效，直至过期）</li>
 *   <li>冷却表（服务器重启后继续生效）</li>
 * </ul>
 * 写盘为同步小文件，仅在指令执行/验证时触发，频率很低。
 */
public class StateStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);

    /** 验证码记录的持久化形态（VCM 的 CodeRecord 映射为 Gson 可序列化结构）。 */
    public static class CodeEntry {
        public String code;
        public long expiry;
        public int attempts;
        public String purpose;
        /** 点击激活链接的一次性 token（仅绑定用途） */
        public String token;
    }

    private static class Data {
        public String version = "1";
        public Map<String, CodeEntry> codes = new HashMap<>();
        public Map<String, Long> cooldowns = new HashMap<>();
    }

    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public StateStorage() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("easyauthreset_state.json");
        load();
    }

    // ---- 验证码 ----

    public CodeEntry getCode(String uuid) {
        return codes.get(uuid);
    }

    public void putCode(String uuid, CodeEntry entry) {
        codes.put(uuid, entry);
        save();
    }

    public void removeCode(String uuid) {
        if (codes.remove(uuid) != null) {
            save();
        }
    }

    public Map<String, CodeEntry> getCodes() {
        return codes;
    }

    // ---- 冷却 ----

    public Long getCooldown(String uuid) {
        return cooldowns.get(uuid);
    }

    public void putCooldown(String uuid, long endMillis) {
        cooldowns.put(uuid, endMillis);
        save();
    }

    public void removeCooldown(String uuid) {
        if (cooldowns.remove(uuid) != null) {
            save();
        }
    }

    public Map<String, Long> getCooldowns() {
        return cooldowns;
    }

    // ---- 持久化 ----

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Data data = gson.fromJson(reader, Data.class);
            if (data != null) {
                if (data.codes != null) {
                    long now = System.currentTimeMillis();
                    data.codes.forEach((uuid, entry) -> {
                        if (entry != null && entry.expiry > now) {
                            codes.put(uuid, entry);
                        }
                    });
                }
                if (data.cooldowns != null) {
                    long now = System.currentTimeMillis();
                    data.cooldowns.forEach((uuid, end) -> {
                        if (end != null && end > now) {
                            cooldowns.put(uuid, end);
                        }
                    });
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read easyauthreset_state.json; ignoring saved state", e);
        }
    }

    public void save() {
        Data data = new Data();
        data.codes.putAll(codes);
        data.cooldowns.putAll(cooldowns);
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write easyauthreset_state.json", e);
        }
    }

    public void shutdown() {
        save();
    }
}
