package com.takumistudios.liveevents.server.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.takumistudios.liveevents.core.command.StartOptions;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.Perms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Administrative targeting; all authority remains on the server command source. */
final class MeteorCommands {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = literal("meteor").requires(Perms.require(Perms.EDIT, PermissionLevel.ADMINS));
        for (String action : List.of("mode", "strike")) root.then(literal(action)
                .then(argument("id", IntegerArgumentType.integer(1))
                        .then(argument("options", StringArgumentType.greedyString()).executes(c -> {
                            try {
                                var services = LiveEventsServer.get();
                                var instance = services.director().get(IntegerArgumentType.getInteger(c, "id")).orElseThrow(() -> new IllegalArgumentException("Unknown instance"));
                                var module = instance.definition().allModules().stream().filter(m -> m.shortType().equals("meteor_impacts"))
                                        .filter(m -> m.fromTicks() <= instance.eventTicks() && instance.eventTicks() < instance.durationSeconds()*20)
                                        .findFirst().orElseThrow(() -> new IllegalArgumentException("No active meteor module"));
                                String text = StringArgumentType.getString(c, "options");
                                String[] tokens = text.split("\\s+");
                                Map<String, String> settings = new HashMap<>();
                                for (String token : tokens) if (token.contains("=")) {
                                    String[] pair = token.split("=", 2); settings.put(pair[0], pair[1]);
                                }
                                var src = c.getSource();
                                String targetMode = action.equals("strike") ? (tokens[0].equals("on") ? "player" : "coordinates") : tokens[0];
                                if (!targetMode.equals("random") && !Perms.check(src, Identifier.fromNamespaceAndPath("liveevents", "meteor.target." + targetMode), PermissionLevel.ADMINS))
                                    throw new IllegalArgumentException("Target permission denied");
                                if (action.equals("strike")) {
                                    if (!tokens[0].equals("on") && !tokens[0].equals("at")) throw new IllegalArgumentException("Expected on or at");
                                    StartOptions options = StartOptions.parse(text);
                                    if (options.in().isPresent() && !options.in().get().equals(instance.dimension().toString()))
                                        throw new IllegalArgumentException("Target dimension differs from event");
                                    Vec3 at;
                                    if (options.on().isPresent()) {
                                        var player = src.getServer().getPlayerList().getPlayerByName(options.on().get());
                                        if (player == null || player.level() != instance.level() || player.isSpectator()) throw new IllegalArgumentException("Invalid player target");
                                        at = player.position();
                                        double spread = options.params().getOrDefault("spread", 0.0);
                                        if (spread < 0 || spread > 128) throw new IllegalArgumentException("spread must be 0..128");
                                        var r = new com.takumistudios.liveevents.core.random.DeterministicRandom(instance.seed() ^ instance.meteors.announced().size());
                                        double a = r.range(0, Math.PI * 2), d = spread * Math.sqrt(r.nextDouble());
                                        int x = (int) Math.floor(at.x + Math.cos(a) * d), z = (int) Math.floor(at.z + Math.sin(a) * d);
                                        if (!instance.level().hasChunk(x >> 4, z >> 4)) throw new IllegalArgumentException("Target chunk is unloaded");
                                        at = new Vec3(x + 0.5, instance.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
                                    } else {
                                        String[] xyz = options.at().orElseThrow(() -> new IllegalArgumentException("Expected coordinates"));
                                        at = new Vec3(StartOptions.coordinate(xyz[0], src.getPosition().x), StartOptions.coordinate(xyz[1], src.getPosition().y), StartOptions.coordinate(xyz[2], src.getPosition().z));
                                    }
                                    instance.meteors.strike(module.index(), at, options.params().getOrDefault("power", instance.vars().getOrDefault("power", 0.7)));
                                } else {
                                    UUID player = null; Vec3 point = null;
                                    if (targetMode.equals("player")) {
                                        var target = src.getServer().getPlayerList().getPlayerByName(settings.getOrDefault("player", ""));
                                        if (target == null) throw new IllegalArgumentException("Expected player=<name>"); player = target.getUUID();
                                    }
                                    if (targetMode.equals("coordinates")) point = new Vec3(number(settings,"x", src.getPosition().x), number(settings,"y",src.getPosition().y), number(settings,"z",src.getPosition().z));
                                    String fallback=settings.getOrDefault("fallback","random");
                                    if(!Set.of("random","wait").contains(fallback)) throw new IllegalArgumentException("fallback must be random or wait");
                                    double minimum=number(settings,"min",3);
                                    if(minimum!=Math.rint(minimum)) throw new IllegalArgumentException("min must be an integer");
                                    instance.meteors.configure(targetMode, player, point, (int) number(settings,"min",3), number(settings,"group_radius",24),
                                            number(settings,"spread",8), number(settings,"random_weight",70), number(settings,"groups_weight",30));
                                    instance.meteors.targetingPolicy(Boolean.parseBoolean(settings.getOrDefault("include_creative","false")),fallback.equals("wait"),number(settings,"rest",10));
                                }
                                services.audit().log(src.getTextName(), "meteor_" + action, "#" + instance.instanceId() + " " + text + " dimension=" + instance.dimension());
                                src.sendSuccess(() -> Component.translatable("liveevents.meteor.updated", instance.instanceId(), action), true);
                                return 1;
                            } catch (IllegalArgumentException e) {
                                c.getSource().sendFailure(Component.translatable("liveevents.error.options", e.getMessage())); return 0;
                            } catch (RuntimeException e) {
                                com.takumistudios.liveevents.LiveEvents.LOGGER.error("Meteor command failed", e);
                                c.getSource().sendFailure(Component.translatable("liveevents.error.options", "meteor")); return 0;
                            }
                        }))));
        return root;
    }
    private static double number(Map<String,String> options, String key, double fallback) {
        double n = Double.parseDouble(options.getOrDefault(key, String.valueOf(fallback)));
        if (!Double.isFinite(n)) throw new IllegalArgumentException("Invalid " + key); return n;
    }
}
