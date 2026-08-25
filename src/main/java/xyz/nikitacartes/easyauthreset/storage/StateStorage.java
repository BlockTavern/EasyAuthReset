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
 * </ul>
 * 冷却<b>不</b>持久化（服务器重启即清空：服务端重启通常为运维操作，不应让玩家背负残留冷却）。
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
    }

    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

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

    // ---- 持久化 ----

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Data data = gson.fromJson(reader, Data.class);
            if (data != null && data.codes != null) {
                long now = System.currentTimeMillis();
                data.codes.forEach((uuid, entry) -> {
                    if (entry != null && entry.expiry > now) {
                        codes.put(uuid, entry);
                    }
                });
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read easyauthreset_state.json; ignoring saved state", e);
        }
    }

    public void save() {
        Data data = new Data();
        data.codes.putAll(codes);
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
