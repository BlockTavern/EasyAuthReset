package xyz.nikitacartes.easyauthreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import xyz.nikitacartes.easyauthreset.EasyAuthReset;
import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;
import xyz.nikitacartes.easyauthreset.handler.PasswordResetHandler;
import xyz.nikitacartes.easyauthreset.util.Lang;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /resetpassword 指令：
 * <pre>
 *   /resetpassword                         无参数：若已绑定邮箱则直接走绑定邮箱，否则显示用法
 *   /resetpassword &lt;邮箱&gt;                第一步：申请重置，发送验证码邮件（已绑定则发往绑定邮箱）
 *   /resetpassword confirm &lt;验证码&gt;       第二步：确认验证码，重置密码
 *   /resetpassword bind &lt;邮箱&gt;          绑定：向邮箱发送绑定验证码（验证所有权）
 *   /resetpassword bind confirm &lt;验证码&gt;  绑定确认：完成邮箱绑定
 * </pre>
 * 未登录玩家同样可以执行（玩家实体已建立，指令注册与登录状态无关）。
 *
 * <p><b>解析要点（已实测验证）</b>：Brigadier 的 string() 不允许 @ 和 .（会把邮箱截断），
 * 因此邮箱/验证码参数必须用 greedyString()；且子命令字面量（bind/confirm）必须排在
 * email 参数之前，否则输入 "bind xxx" 会被当成邮箱参数。</p>
 */
public class ResetPasswordCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("resetpassword")
                // 无参数：已绑定则直接以绑定邮箱申请重置
                .executes(ResetPasswordCommand::requestRoot)
                .then(literal("bind")
                        .executes(ctx -> {
                            sendLang(ctx, "usage");
                            return 0;
                        })
                        .then(literal("confirm")
                                .then(argument("code", StringArgumentType.greedyString())
                                        .executes(ResetPasswordCommand::bindConfirm)))
                        .then(argument("email", StringArgumentType.greedyString())
                                .executes(ResetPasswordCommand::bindRequest)))
                .then(literal("confirm")
                        .then(argument("code", StringArgumentType.greedyString())
                                .executes(ResetPasswordCommand::confirm)))
                .then(argument("email", StringArgumentType.greedyString())
                        .executes(ResetPasswordCommand::request)));
    }

    private static int requestRoot(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            sendLang(ctx, "onlyPlayers");
            return 0;
        }
        PasswordResetHandler handler = EasyAuthReset.getInstance().getResetHandler();
        switch (handler.requestReset(player, null)) {
            case SUCCESS -> sendLang(ctx, "sendingCode", "绑定邮箱");
            case COOLDOWN -> sendCooldown(ctx, handler, player);
            case NOT_REGISTERED -> sendLang(ctx, "notRegistered");
            case INVALID_EMAIL -> sendLang(ctx, "invalidEmail");
            case INTERNAL_ERROR -> sendLang(ctx, "dbNotReady");
            case BIND_REQUIRED -> sendLang(ctx, "bindRequired");
            case NO_EMAIL -> sendLang(ctx, "usage");
            case IP_BLOCKED -> sendLang(ctx, "ipMismatchBlocked");
        }
        return 1;
    }

    private static int request(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            sendLang(ctx, "onlyPlayers");
            return 0;
        }
        String email = StringArgumentType.getString(ctx, "email");
        PasswordResetHandler handler = EasyAuthReset.getInstance().getResetHandler();

        switch (handler.requestReset(player, email)) {
            case SUCCESS -> sendLang(ctx, "sendingCode", email);
            case COOLDOWN -> sendCooldown(ctx, handler, player);
            case NOT_REGISTERED -> sendLang(ctx, "notRegistered");
            case INVALID_EMAIL -> sendLang(ctx, "invalidEmail");
            case INTERNAL_ERROR -> sendLang(ctx, "dbNotReady");
            case BIND_REQUIRED -> sendLang(ctx, "bindRequired");
            case NO_EMAIL -> sendLang(ctx, "usage");
            case IP_BLOCKED -> sendLang(ctx, "ipMismatchBlocked");
        }
        return 1;
    }

    private static int confirm(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            sendLang(ctx, "onlyPlayers");
            return 0;
        }
        String code = StringArgumentType.getString(ctx, "code");
        boolean ok = EasyAuthReset.getInstance().getResetHandler().confirmAndReset(player, code);
        return ok ? 1 : 0;
    }

    private static int bindRequest(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            sendLang(ctx, "onlyPlayers");
            return 0;
        }
        String email = StringArgumentType.getString(ctx, "email");
        PasswordResetHandler handler = EasyAuthReset.getInstance().getResetHandler();

        switch (handler.bindRequest(player, email)) {
            case SUCCESS -> sendLang(ctx, "sendingCode", email);
            case COOLDOWN -> sendCooldown(ctx, handler, player);
            case NOT_REGISTERED -> sendLang(ctx, "notRegisteredBind");
            case INVALID_EMAIL -> sendLang(ctx, "invalidEmail");
            case INTERNAL_ERROR -> sendLang(ctx, "dbNotReady");
            case OWNER_MANAGED -> sendLang(ctx, "bindNotNeeded");
            case IP_BLOCKED -> sendLang(ctx, "ipMismatchBlocked");
            default -> sendLang(ctx, "usage");
        }
        return 1;
    }

    private static int bindConfirm(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            sendLang(ctx, "onlyPlayers");
            return 0;
        }
        String code = StringArgumentType.getString(ctx, "code");
        boolean ok = EasyAuthReset.getInstance().getResetHandler().bindConfirm(player, code);
        return ok ? 1 : 0;
    }

    private static void sendCooldown(CommandContext<ServerCommandSource> ctx,
                                     PasswordResetHandler handler, ServerPlayerEntity player) {
        final EasyAuthResetConfig config = EasyAuthReset.getInstance().getConfig();
        final long seconds = handler.cooldownRemaining(player.getUuidAsString());
        ctx.getSource().sendFeedback(() -> Text.literal(Lang.msg(config, "cooldown", seconds)), false);
    }

    private static void sendLang(CommandContext<ServerCommandSource> ctx, String key, Object... args) {
        ctx.getSource().sendFeedback(() -> Text.literal(
                Lang.msg(EasyAuthReset.getInstance().getConfig(), key, args)), false);
    }
}
