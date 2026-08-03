package com.mcmod.monsterwaves.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;

/**
 * 属性球类型参数：允许字母/数字/_/-/./:（支持自定义类型名如 ATTACK，也支持原版/模组属性注册名如 minecraft:generic.movement_speed）。
 * 不使用 word()/string() 的原因：它们不允许冒号（Brigadier 限制）。
 */
public class AttributeTypeArgument implements ArgumentType<String> {
    public static AttributeTypeArgument type() {
        return new AttributeTypeArgument();
    }

    public static String getType(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS
                    .dispatcherUnknownArgument().createWithContext(reader);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isAllowed(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("ATTACK", "minecraft:generic.movement_speed");
    }
}
