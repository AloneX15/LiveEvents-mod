package com.takumistudios.liveevents.server.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.command.StartOptions;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.PhaseDef;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.definition.ValidationIssue;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.network.Payloads;
import com.takumistudios.liveevents.network.ServerNet;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.Perms;
import com.takumistudios.liveevents.server.SkyScarManager;
import com.takumistudios.liveevents.server.instance.EventDirector;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import com.takumistudios.liveevents.server.schedule.ScheduleService;
import com.takumistudios.liveevents.util.AtomicFiles;
import com.takumistudios.liveevents.util.Texts;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /liveevents} (alias {@code /le}): todos los comandos de §11. Funcionan desde consola, bloques de comandos,
 * funciones y RCON. Cada subcomando comprueba su permiso (§10) y todo error se convierte en un mensaje, nunca en un
 * crash.
 */
public final class LiveEventsCommand {
    private static final PermissionLevel OP2 = PermissionLevel.GAMEMASTERS;
    private static final PermissionLevel OP3 = PermissionLevel.ADMINS;

    private LiveEventsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(build("liveevents"));
        dispatcher.register(literal("le").requires(Perms.require(Perms.COMMAND, OP2)).redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return literal(name).requires(Perms.require(Perms.COMMAND, OP2))
                .then(MeteorCommands.build())
                .then(literal("list").requires(Perms.require(Perms.LIST, OP2))
                        .executes(c -> listDefinitions(c.getSource()))
                        .then(literal("definitions").executes(c -> listDefinitions(c.getSource())))
                        .then(literal("active").executes(c -> listActive(c.getSource()))))
                .then(literal("status").requires(Perms.require(Perms.LIST, OP2))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES).executes(c -> instance(c).map(i -> {
                            c.getSource().sendSuccess(() -> Component.translatable("liveevents.meteor.status",i.instanceId(),trim(i.vars().getOrDefault("power",0.7)),
                                    trim(i.vars().getOrDefault("brutality",0.5)),trim(i.elapsedSeconds()),trim(Math.max(0,i.durationSeconds()-i.elapsedSeconds())),
                                    i.meteors.mode(),i.meteors.launched(),i.meteors.throttled()),false);return 1;
                        }).orElse(0))))
                .then(literal("info").requires(Perms.require(Perms.LIST, OP2))
                        .then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                .executes(c -> info(c.getSource(), eventId(c, "event")))))
                .then(literal("start")
                        .then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                .executes(c -> start(c, ""))
                                .then(argument("options", StringArgumentType.greedyString()).suggests(START_OPTIONS)
                                        .executes(c -> start(c, StringArgumentType.getString(c, "options"))))))
                .then(literal("preview").requires(Perms.require(Perms.PREVIEW, OP2))
                        .then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                .executes(c -> preview(c, ""))
                                .then(argument("options", StringArgumentType.greedyString()).suggests(START_OPTIONS)
                                        .executes(c -> preview(c, StringArgumentType.getString(c, "options"))))))
                .then(literal("pause").requires(Perms.require(Perms.CONTROL, OP2))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES).executes(c -> pause(c, true))))
                .then(literal("resume").requires(Perms.require(Perms.CONTROL, OP2))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES).executes(c -> pause(c, false))))
                .then(literal("skip").requires(Perms.require(Perms.CONTROL, OP2))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES).executes(c -> skip(c, null))
                                .then(argument("phase", StringArgumentType.word()).suggests(PHASES)
                                        .executes(c -> skip(c, StringArgumentType.getString(c, "phase"))))))
                .then(literal("seek").requires(Perms.require(Perms.DEBUG, OP3))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES)
                                .then(argument("second", DoubleArgumentType.doubleArg(0)).executes(LiveEventsCommand::seek))))
                .then(literal("timescale").requires(Perms.require(Perms.DEBUG, OP3))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES)
                                .then(argument("value", DoubleArgumentType.doubleArg(0.1, 2.0)).executes(LiveEventsCommand::timescale))))
                .then(literal("set").requires(Perms.require(Perms.EDIT, OP3))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES)
                                .then(argument("param", StringArgumentType.word()).suggests(PARAMS)
                                        .then(argument("value", StringArgumentType.word()).executes(LiveEventsCommand::set)))))
                .then(literal("stop").requires(Perms.require(Perms.STOP, OP2))
                        .then(literal("all").executes(c -> stopAll(c.getSource(), false))
                                .then(literal("confirm").executes(c -> stopAll(c.getSource(), true))))
                        .then(argument("id", IntegerArgumentType.integer(1)).suggests(INSTANCES).executes(LiveEventsCommand::stop)))
                .then(literal("revert").requires(Perms.require(Perms.REVERT, OP3))
                        .then(literal("list").executes(c -> listJournals(c.getSource())))
                        .then(argument("target", StringArgumentType.word()).suggests(JOURNALS)
                                .executes(c -> revert(c, false))
                                .then(literal("confirm").executes(c -> revert(c, true)))))
                .then(literal("reload").requires(Perms.require(Perms.RELOAD, OP3)).executes(c -> reload(c.getSource())))
                .then(literal("validate").requires(Perms.require(Perms.VALIDATE, OP2))
                        .executes(c -> validate(c.getSource(), "all"))
                        .then(literal("all").executes(c -> validate(c.getSource(), "all")))
                        .then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                .executes(c -> validate(c.getSource(), eventId(c, "event")))))
                .then(literal("create").requires(Perms.require(Perms.CREATE, OP3))
                        .then(argument("new_id", IdentifierArgument.id())
                                .then(literal("from").then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                        .executes(LiveEventsCommand::create)))))
                .then(literal("scars").requires(Perms.require(Perms.EDIT, OP3))
                        .then(literal("list").executes(c -> scarsList(c.getSource())))
                        .then(literal("clear")
                                .then(literal("all").executes(c -> scarsClear(c.getSource(), -1)))
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> scarsClear(c.getSource(), IntegerArgumentType.getInteger(c, "id"))))))
                .then(literal("panic").requires(Perms.require(Perms.PANIC, OP2)).executes(c -> panic(c.getSource())))
                .then(literal("debug").requires(Perms.require(Perms.DEBUG, OP3))
                        .then(literal("timeline").executes(c -> debugTimeline(c.getSource())))
                        .then(literal("perf").executes(c -> debugPerf(c.getSource())))
                        .then(literal("report").executes(c -> debugReport(c.getSource()))))
                .then(literal("schedule").requires(Perms.require(Perms.SCHEDULE, OP3))
                        .then(literal("list").executes(c -> scheduleList(c.getSource())))
                        .then(literal("cancel").then(argument("id", IntegerArgumentType.integer(1)).executes(c -> scheduleCancel(c))))
                        .then(argument("event", IdentifierArgument.id()).suggests(EVENTS)
                                .then(literal("at").then(argument("when", StringArgumentType.greedyString())
                                        .executes(LiveEventsCommand::schedule)))))
                .then(literal("sequence").requires(Perms.require(Perms.SCHEDULE, OP3))
                        .then(literal("list").executes(c -> sequenceList(c.getSource())))
                        .then(literal("stop").executes(c -> sequenceStop(c.getSource())))
                        .then(literal("start").then(argument("sequence", IdentifierArgument.id()).suggests(SEQUENCES)
                                .executes(LiveEventsCommand::sequenceStart))))
                .then(literal("gui").requires(Perms.require(Perms.GUI, OP2)).executes(c -> gui(c.getSource())));
    }

    // ================================================================================================ sugerencias

    private static LiveEventsServer services() {
        return LiveEventsServer.get();
    }

    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) ->
            suggestQuoted(services().registry().definitions().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> SEQUENCES = (c, b) ->
            suggestQuoted(services().registry().sequences().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> INSTANCES = (c, b) ->
            SharedSuggestionProvider.suggest(services().director().active().stream().map(i -> String.valueOf(i.instanceId())), b);
    private static final SuggestionProvider<CommandSourceStack> PHASES = (c, b) -> {
        Optional<EventInstance> i = services().director().get(IntegerArgumentType.getInteger(c, "id"));
        return SharedSuggestionProvider.suggest(i.map(x -> x.definition().phases().stream().map(PhaseDef::id).toList()).orElse(List.of()), b);
    };
    private static final SuggestionProvider<CommandSourceStack> PARAMS = (c, b) -> {
        Optional<EventInstance> i = services().director().get(IntegerArgumentType.getInteger(c, "id"));
        return SharedSuggestionProvider.suggest(i.map(x -> List.copyOf(x.vars().keySet())).orElse(List.of()), b);
    };
    private static final SuggestionProvider<CommandSourceStack> JOURNALS = (c, b) -> {
        List<String> out = new ArrayList<>();
        out.add("last");
        services().director().active().forEach(i -> out.add(String.valueOf(i.instanceId())));
        services().journal().all().stream().filter(m -> !m.reverted).forEach(m -> out.add(m.id));
        return suggestQuoted(out, b);
    };
    /** Sugiere la siguiente palabra clave de las opciones de start. */
    private static final SuggestionProvider<CommandSourceStack> START_OPTIONS = (c, b) -> {
        String typed = b.getRemaining();
        int lastSpace = typed.lastIndexOf(' ');
        SuggestionsBuilder sub = b.createOffset(b.getStart() + lastSpace + 1);
        String prev = lastSpace < 0 ? "" : typed.substring(0, lastSpace).trim();
        String prevWord = prev.isEmpty() ? "" : prev.substring(prev.lastIndexOf(' ') + 1);
        if (prevWord.equals("on")) {
            return SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), sub);
        }
        if (prevWord.equals("in")) {
            return SharedSuggestionProvider.suggest(services().config().allowed_dimensions, sub);
        }
        List<String> options = new ArrayList<>(StartOptions.KEYWORDS);
        try {
            String id = eventId(c, "event");
            services().registry().get(id).ifPresent(d -> d.params().keySet().forEach(p -> options.add(p + "=")));
        } catch (IllegalArgumentException ignored) {
            // sin evento todavía
        }
        return SharedSuggestionProvider.suggest(options, sub);
    };

    private static CompletableFuture<Suggestions> suggestQuoted(Iterable<String> values, SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(values, b);
    }

    /**
     * Id de evento o secuencia: "supernova" (Brigadier lo lee como minecraft:supernova) se busca también como
     * liveevents:supernova.
     */
    private static String eventId(CommandContext<CommandSourceStack> c, String name) {
        Identifier id = IdentifierArgument.getId(c, name);
        String full = id.toString();
        if (id.getNamespace().equals("minecraft") && !services().registry().definitions().containsKey(full)
                && !services().registry().sequences().containsKey(full)) {
            return "liveevents:" + id.getPath();
        }
        return full;
    }

    // ================================================================================================ listar e info

    private static int listDefinitions(CommandSourceStack src) {
        Map<String, EventDefinition> defs = services().registry().definitions();
        src.sendSuccess(() -> Component.translatable("liveevents.list.definitions", defs.size()).withStyle(ChatFormatting.GOLD), false);
        for (EventDefinition d : defs.values()) {
            src.sendSuccess(() -> Component.literal(" • ").append(Component.literal(d.id()).withStyle(ChatFormatting.AQUA))
                    .append(" — ").append(Texts.of(d.displayName())).append(Component.literal(String.format(Locale.ROOT, " (%s, %ss)",
                            d.scope().id(), trim(Durations.ticksToSeconds(d.durationTicks())))).withStyle(ChatFormatting.GRAY)), false);
        }
        long errors = services().registry().issues().stream().filter(ValidationIssue::isError).count();
        if (errors > 0) {
            src.sendSuccess(() -> Component.translatable("liveevents.list.invalid", errors).withStyle(ChatFormatting.RED), false);
        }
        return defs.size();
    }

    private static int listActive(CommandSourceStack src) {
        var active = services().director().active();
        src.sendSuccess(() -> Component.translatable("liveevents.list.active", active.size(), services().director().queued())
                .withStyle(ChatFormatting.GOLD), false);
        for (EventInstance i : active) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " #%d ", i.instanceId())).withStyle(ChatFormatting.YELLOW)
                    .append(Texts.of(i.definition().displayName()))
                    .append(Component.literal(String.format(Locale.ROOT, " %s/%s · %s · %s%s%s", Durations.formatClock(i.elapsedSeconds()),
                            Durations.formatClock(i.durationSeconds()), i.currentPhase().orElse("-"), i.profile(),
                            i.paused() ? " · ⏸" : "", i.preview() ? " · preview" : "")).withStyle(ChatFormatting.GRAY)), false);
        }
        return active.size();
    }

    private static int info(CommandSourceStack src, String id) {
        Optional<EventDefinition> found = services().registry().get(id);
        if (found.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.unknown_event", id));
            return 0;
        }
        EventDefinition d = found.get();
        src.sendSuccess(() -> Component.literal("").append(Texts.of(d.displayName()).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" (" + d.id() + ")").withStyle(ChatFormatting.GRAY)), false);
        if (!d.description().isEmpty()) {
            src.sendSuccess(() -> Texts.of(d.description()).copy().withStyle(ChatFormatting.ITALIC), false);
        }
        src.sendSuccess(() -> Component.translatable("liveevents.info.summary", d.scope().id(), trim(Durations.ticksToSeconds(d.durationTicks())),
                String.join(", ", d.allowedDimensions()), d.lethal() ? "✔" : "✘", d.defaultProfile().map(Profile::id).orElse("-")), false);
        if (!d.params().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            d.params().forEach((k, v) -> sb.append(k).append('=').append(trim(v)).append(' '));
            src.sendSuccess(() -> Component.translatable("liveevents.info.params", sb.toString().trim()), false);
        }
        for (PhaseDef p : d.phases()) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " %s – %s | ", Durations.formatClock(Durations.ticksToSeconds(p.fromTicks())),
                            Durations.formatClock(Durations.ticksToSeconds(p.toTicks())))).withStyle(ChatFormatting.YELLOW)
                    .append(Texts.of(p.title())).append(Component.literal(" [" + p.id() + ", " + p.modules().size() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
            if (!p.descChaos().isEmpty()) {
                src.sendSuccess(() -> Component.literal("   ").append(Texts.of(p.descChaos())).withStyle(ChatFormatting.GRAY), false);
            }
        }
        if (!d.aftermath().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("liveevents.info.aftermath", String.join(", ", d.aftermath()),
                    d.aftermathTicks() >= Durations.MAX_TICKS ? "permanent" : trim(d.aftermathTicks() / (double) Durations.TICKS_PER_GAME_DAY) + "d"), false);
        }
        return 1;
    }

    // ================================================================================================ lanzar

    private static int start(CommandContext<CommandSourceStack> c, String optionsText) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        String id = eventId(c, "event");
        Optional<EventDefinition> def = services().registry().get(id);
        if (def.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.unknown_event", id));
            return 0;
        }
        if (!Perms.canStart(src, def.get().id())) {
            src.sendFailure(Component.translatable("liveevents.error.no_permission_event", def.get().id()));
            return 0;
        }
        String actorKey = src.getEntity() != null ? src.getEntity().getStringUUID() : src.getTextName();
        if (src.isPlayer() && !services().startLimiter().tryAcquire(actorKey, System.currentTimeMillis())) {
            src.sendFailure(Component.translatable("liveevents.error.rate_limited", services().config().start_rate_limit));
            return 0;
        }
        return launch(src, def.get(), optionsText, false);
    }

    private static int preview(CommandContext<CommandSourceStack> c, String optionsText) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer player = src.getPlayerOrException();
        String id = eventId(c, "event");
        Optional<EventDefinition> def = services().registry().get(id);
        if (def.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.unknown_event", id));
            return 0;
        }
        return player == null ? 0 : launch(src, def.get(), optionsText, true);
    }

    private static int launch(CommandSourceStack src, EventDefinition def, String optionsText, boolean preview) throws CommandSyntaxException {
        StartOptions options;
        try {
            options = StartOptions.parse(optionsText);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.translatable("liveevents.error.options", e.getMessage()));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        boolean elsewhere = false;
        if (options.in().isPresent()) {
            ServerLevel target = src.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(options.in().get())));
            if (target == null) {
                src.sendFailure(Component.translatable("liveevents.error.unknown_dimension", options.in().get()));
                return 0;
            }
            elsewhere |= target != level;
            level = target;
        }
        if (options.at().isPresent()) {
            String[] xyz = options.at().get();
            pos = new Vec3(StartOptions.coordinate(xyz[0], pos.x), StartOptions.coordinate(xyz[1], pos.y), StartOptions.coordinate(xyz[2], pos.z));
            elsewhere = true;
        } else if (options.on().isPresent()) {
            Entity target = EntityArgument.entity().parse(new StringReader(options.on().get())).findSingleEntity(src);
            pos = target.position();
            if (target.level() instanceof ServerLevel l) {
                level = l;
            }
            elsewhere = true;
        }
        if (elsewhere && src.isPlayer() && !Perms.check(src, Perms.START_ANYWHERE, OP3)) {
            src.sendFailure(Component.translatable("liveevents.error.no_permission_anywhere"));
            return 0;
        }
        ServerPlayer viewer = preview ? src.getPlayerOrException() : null;
        EventDirector.StartResult result = services().director().start(new EventDirector.StartRequest(def.id(), level, pos,
                options.profile().flatMap(Profile::parse), options.timescale(), options.params(), preview, viewer,
                options.queue(), options.confirm(), src.getTextName(), options.delaySeconds()));
        if (result.ok()) {
            src.sendSuccess(result::message, !preview);
            return result.instance() != null ? result.instance().instanceId() : 1;
        }
        src.sendFailure(result.message());
        return 0;
    }

    // ================================================================================================ control

    private static Optional<EventInstance> instance(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        Optional<EventInstance> i = services().director().get(id);
        if (i.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.unknown_instance", id));
        }
        return i;
    }

    private static int pause(CommandContext<CommandSourceStack> c, boolean paused) {
        return instance(c).map(i -> {
            services().director().setPaused(i, paused);
            services().audit().log(c.getSource().getTextName(), paused ? "pause" : "resume", i.definitionId() + " #" + i.instanceId());
            c.getSource().sendSuccess(() -> Component.translatable(paused ? "liveevents.paused" : "liveevents.resumed", i.instanceId()), true);
            return 1;
        }).orElse(0);
    }

    private static int skip(CommandContext<CommandSourceStack> c, String phase) {
        return instance(c).map(i -> {
            if (!services().director().skip(i, phase)) {
                c.getSource().sendFailure(Component.translatable("liveevents.error.unknown_phase", phase == null ? "next" : phase));
                return 0;
            }
            c.getSource().sendSuccess(() -> Component.translatable("liveevents.skipped", i.instanceId(), i.currentPhase().orElse("-")), true);
            return 1;
        }).orElse(0);
    }

    private static int seek(CommandContext<CommandSourceStack> c) {
        double second = DoubleArgumentType.getDouble(c, "second");
        return instance(c).map(i -> {
            services().director().seek(i, second * Durations.TICKS_PER_SECOND);
            c.getSource().sendSuccess(() -> Component.translatable("liveevents.seeked", i.instanceId(), Durations.formatClock(second)), true);
            return 1;
        }).orElse(0);
    }

    private static int timescale(CommandContext<CommandSourceStack> c) {
        double value = DoubleArgumentType.getDouble(c, "value");
        return instance(c).map(i -> {
            services().director().setTimescale(i, value);
            c.getSource().sendSuccess(() -> Component.translatable("liveevents.timescale", i.instanceId(), trim(value)), true);
            return 1;
        }).orElse(0);
    }

    private static int set(CommandContext<CommandSourceStack> c) {
        String param = StringArgumentType.getString(c, "param");
        double value;
        try {
            String text = StringArgumentType.getString(c, "value");
            value = (param.equals("duration") || param.equals("remaining")) ? com.takumistudios.liveevents.core.time.Durations.parseTicks(text) / 20.0 : Double.parseDouble(text);
        } catch (RuntimeException e) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.options", "value")); return 0;
        }
        if (!param.matches("[a-z0-9_]+") || !Double.isFinite(value)) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.options", param));
            return 0;
        }
        return instance(c).map(i -> {
            try { services().director().set(i, param, value); }
            catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.translatable("liveevents.error.options", e.getMessage())); return 0; }
            services().audit().log(c.getSource().getTextName(), "set", i.definitionId() + " #" + i.instanceId() + " " + param + "=" + value);
            c.getSource().sendSuccess(() -> Component.translatable("liveevents.set", i.instanceId(), param, trim(param.equals("remaining") ? i.durationSeconds()-i.elapsedSeconds() : i.vars().getOrDefault(param,value))), true);
            return 1;
        }).orElse(0);
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!services().director().stop(id, c.getSource().getTextName())) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.unknown_instance", id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("liveevents.stopped", id), true);
        return 1;
    }

    private static int stopAll(CommandSourceStack src, boolean confirm) {
        if (!confirm) {
            src.sendFailure(Component.translatable("liveevents.confirm_needed", "/le stop all confirm"));
            return 0;
        }
        int n = services().director().stopAll(src.getTextName());
        src.sendSuccess(() -> Component.translatable("liveevents.stopped_all", n), true);
        return n;
    }

    private static int panic(CommandSourceStack src) {
        int n = services().director().panic(src.getTextName());
        src.sendSuccess(() -> Component.translatable("liveevents.panic", n).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return Math.max(1, n);
    }

    // ================================================================================================ revertir

    private static int listJournals(CommandSourceStack src) {
        List<WorldJournal.Meta> metas = new ArrayList<>(services().journal().all());
        metas.sort(Comparator.comparingLong((WorldJournal.Meta m) -> m.created_millis).reversed());
        src.sendSuccess(() -> Component.translatable("liveevents.revert.list", metas.size()).withStyle(ChatFormatting.GOLD), false);
        for (WorldJournal.Meta m : metas.subList(0, Math.min(15, metas.size()))) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " %s · %s · #%d · %d %s%s", m.id, m.event_id, m.instance_id,
                    m.entries, "changes", m.reverted ? " · reverted" : "")).withStyle(m.reverted ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY), false);
        }
        return metas.size();
    }

    private static int revert(CommandContext<CommandSourceStack> c, boolean confirm) {
        CommandSourceStack src = c.getSource();
        String target = StringArgumentType.getString(c, "target");
        Optional<WorldJournal.Meta> meta;
        if (target.equals("last")) {
            meta = services().journal().last();
        } else if (target.matches("\\d+")) {
            meta = services().journal().forInstance(Integer.parseInt(target));
        } else {
            meta = services().journal().find(target);
        }
        if (meta.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.no_journal", target));
            return 0;
        }
        WorldJournal.Meta m = meta.get();
        if (services().director().active().stream().anyMatch(i -> i.journal() != null && i.journal().id().equals(m.id))) {
            src.sendFailure(Component.translatable("liveevents.error.journal_active", m.instance_id));
            return 0;
        }
        if (!confirm) {
            src.sendFailure(Component.translatable("liveevents.revert.confirm", m.event_id, m.entries, "/le revert " + target + " confirm"));
            return 0;
        }
        services().director().revert(m, src);
        src.sendSuccess(() -> Component.translatable("liveevents.revert.started", m.event_id, m.entries), true);
        return 1;
    }

    // ================================================================================================ definiciones

    private static int reload(CommandSourceStack src) {
        services().reloadConfig();
        services().registry().reload(src.getServer());
        services().director().clearCooldowns();
        services().director().broadcastDefinitions();
        long errors = services().registry().issues().stream().filter(ValidationIssue::isError).count();
        src.sendSuccess(() -> Component.translatable("liveevents.reloaded", services().registry().definitions().size(), errors), true);
        services().audit().log(src.getTextName(), "reload", "");
        return 1;
    }

    private static int validate(CommandSourceStack src, String which) {
        List<ValidationIssue> issues = services().registry().issues().stream()
                .filter(i -> which.equals("all") || i.eventId().equals(which) || i.eventId().equals("liveevents:" + which)).toList();
        if (issues.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("liveevents.validate.ok", which).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        for (ValidationIssue i : issues.subList(0, Math.min(30, issues.size()))) {
            MutableComponent line = Component.literal(i.isError() ? "✘ " : "⚠ ").withStyle(i.isError() ? ChatFormatting.RED : ChatFormatting.YELLOW)
                    .append(Component.literal(i.eventId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(i.path().isEmpty() ? "" : " → " + i.path()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(": " + i.message()).withStyle(ChatFormatting.WHITE));
            src.sendSuccess(() -> line, false);
        }
        if (issues.size() > 30) {
            src.sendSuccess(() -> Component.translatable("liveevents.validate.more", issues.size() - 30), false);
        }
        return issues.stream().anyMatch(ValidationIssue::isError) ? 0 : 1;
    }

    private static int create(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String newId = IdentifierArgument.getId(c, "new_id").toString();
        String parent = eventId(c, "event");
        if (!newId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || newId.startsWith("liveevents:")) {
            src.sendFailure(Component.translatable("liveevents.error.bad_new_id", newId));
            return 0;
        }
        Optional<EventDefinition> def = services().registry().get(parent);
        if (def.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.unknown_event", parent));
            return 0;
        }
        try {
            Path file = services().registry().writeVariant(newId, def.get().id());
            services().registry().reload(src.getServer());
            services().director().broadcastDefinitions();
            src.sendSuccess(() -> Component.translatable("liveevents.created", newId, "config/liveevents/events/" + file.getFileName()), true);
            services().audit().log(src.getTextName(), "create", newId + " from " + def.get().id());
            return 1;
        } catch (IOException e) {
            src.sendFailure(Component.translatable("liveevents.error.create_failed", e.getMessage()));
            return 0;
        }
    }

    // ================================================================================================ secuelas

    private static int scarsList(CommandSourceStack src) {
        List<SkyScarManager.Scar> scars = services().scars().list();
        src.sendSuccess(() -> Component.translatable("liveevents.scars.list", scars.size()).withStyle(ChatFormatting.GOLD), false);
        for (SkyScarManager.Scar s : scars) {
            String expires = s.expires_game_time < 0 ? "permanent" : String.valueOf(s.expires_game_time);
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " #%d %s · %s · %s · expires %s", s.id, s.kind, s.event_id,
                    s.dimension, expires)).withStyle(ChatFormatting.GRAY), false);
        }
        return scars.size();
    }

    private static int scarsClear(CommandSourceStack src, int id) {
        List<String> dims = services().scars().clear(id);
        for (String dim : dims) {
            ServerLevel level = src.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
            if (level != null) {
                services().director().sendScars(level);
            }
        }
        src.sendSuccess(() -> Component.translatable("liveevents.scars.cleared", id < 0 ? "all" : String.valueOf(id)), true);
        services().audit().log(src.getTextName(), "scars_clear", id < 0 ? "all" : String.valueOf(id));
        return dims.isEmpty() ? 0 : 1;
    }

    // ================================================================================================ depuración

    private static int debugTimeline(CommandSourceStack src) throws CommandSyntaxException {
        boolean on = services().director().toggleDebugTimeline(src.getPlayerOrException());
        src.sendSuccess(() -> Component.translatable(on ? "liveevents.debug.timeline_on" : "liveevents.debug.timeline_off"), false);
        return 1;
    }

    private static int debugPerf(CommandSourceStack src) {
        if (src.getPlayer() != null) {
            boolean on = services().director().toggleDebugPerf(src.getPlayer());
            src.sendSuccess(() -> Component.translatable(on ? "liveevents.debug.perf_on" : "liveevents.debug.perf_off"), false);
        }
        String text = services().director().perfText();
        src.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Informe para adjuntar a un reporte de error: versiones, mods, configuración y errores. Sin datos personales. */
    private static int debugReport(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder();
        sb.append("LiveEvents debug report\n=======================\n");
        sb.append("LiveEvents: ").append(LiveEvents.version()).append('\n');
        sb.append("Minecraft: ").append(SharedConstants.getCurrentVersion().name()).append('\n');
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("Protocol: ").append(Payloads.PROTOCOL).append("\n\nMods:\n");
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            sb.append("  ").append(mod.getMetadata().getId()).append(' ').append(mod.getMetadata().getVersion().getFriendlyString()).append('\n');
        }
        JsonObject config = com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(services().config())).getAsJsonObject();
        config.addProperty("discord_webhook", config.get("discord_webhook").getAsString().isEmpty() ? "" : "<set>");
        config.addProperty("missing_mod_message", "<omitted>");
        sb.append("\nserver.json:\n").append(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(config)).append('\n');
        sb.append("\nDefinitions (").append(services().registry().definitions().size()).append("):\n");
        services().registry().definitions().keySet().forEach(id -> sb.append("  ").append(id).append('\n'));
        sb.append("\nValidation issues:\n");
        services().registry().issues().forEach(i -> sb.append("  ").append(i).append('\n'));
        sb.append("\nActive events:\n");
        services().director().active().forEach(i -> sb.append("  #").append(i.instanceId()).append(' ').append(i.definitionId())
                .append(" t=").append(trim(i.elapsedSeconds())).append("s profile=").append(i.profile()).append('\n'));
        sb.append("\nPerformance: ").append(services().director().perfText()).append('\n');
        Path file = FabricLoader.getInstance().getGameDir().resolve("logs")
                .resolve("liveevents-report-" + LocalDateTime.now().toString().replace(':', '-').replace('.', '-') + ".txt");
        String text = sb.toString();
        services().io().execute(() -> {
            try {
                AtomicFiles.writeString(file, text);
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not write the debug report", e);
            }
        });
        src.sendSuccess(() -> Component.translatable("liveevents.debug.report", "logs/" + file.getFileName()), false);
        return 1;
    }

    // ================================================================================================ programación

    private static int schedule(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        String id = eventId(c, "event");
        Optional<EventDefinition> def = services().registry().get(id);
        if (def.isEmpty()) {
            src.sendFailure(Component.translatable("liveevents.error.unknown_event", id));
            return 0;
        }
        String rest = StringArgumentType.getString(c, "when").trim();
        String whenText = rest.split("\\s+")[0];
        String options = rest.length() > whenText.length() ? rest.substring(whenText.length()).trim() : "";
        ZonedDateTime when;
        try {
            when = LocalDateTime.parse(whenText).atZone(services().config().zone());
        } catch (DateTimeParseException e) {
            src.sendFailure(Component.translatable("liveevents.error.bad_datetime", whenText));
            return 0;
        }
        if (when.isBefore(ZonedDateTime.now(services().config().zone()))) {
            src.sendFailure(Component.translatable("liveevents.error.past_datetime", whenText));
            return 0;
        }
        StartOptions opts;
        try {
            opts = StartOptions.parse(options);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.translatable("liveevents.error.options", e.getMessage()));
            return 0;
        }
        Vec3 pos = src.getPosition();
        if (opts.at().isPresent()) {
            String[] xyz = opts.at().get();
            pos = new Vec3(StartOptions.coordinate(xyz[0], pos.x), StartOptions.coordinate(xyz[1], pos.y), StartOptions.coordinate(xyz[2], pos.z));
        }
        ServerLevel level = src.getLevel();
        if (opts.in().isPresent()) {
            ServerLevel target = src.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(opts.in().get())));
            if (target != null) {
                level = target;
            }
        }
        ScheduleService.Entry e = services().schedule().add(def.get().id(), when, level, pos, opts.profile().flatMap(Profile::parse),
                opts.params(), src.getTextName());
        services().audit().log(src.getTextName(), "schedule", def.get().id() + " at " + e.time);
        src.sendSuccess(() -> Component.translatable("liveevents.schedule.added", e.id, Texts.of(def.get().displayName()), e.time), true);
        return e.id;
    }

    private static int scheduleList(CommandSourceStack src) {
        List<ScheduleService.Entry> entries = services().schedule().list();
        src.sendSuccess(() -> Component.translatable("liveevents.schedule.list", entries.size()).withStyle(ChatFormatting.GOLD), false);
        for (ScheduleService.Entry e : entries) {
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, " #%d %s · %s · %s%s", e.id, e.event_id, e.time, e.dimension,
                    e.missed ? " · missed" : "")).withStyle(e.missed ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        }
        return entries.size();
    }

    private static int scheduleCancel(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!services().schedule().cancel(id)) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.unknown_schedule", id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("liveevents.schedule.cancelled", id), true);
        return 1;
    }

    private static int sequenceStart(CommandContext<CommandSourceStack> c) {
        String id = eventId(c, "sequence");
        if (!services().schedule().startSequence(id, c.getSource())) {
            c.getSource().sendFailure(Component.translatable("liveevents.error.unknown_sequence", id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("liveevents.sequence.started", id), true);
        return 1;
    }

    private static int sequenceStop(CommandSourceStack src) {
        int n = services().schedule().stopSequences();
        src.sendSuccess(() -> Component.translatable("liveevents.sequence.stopped", n), true);
        return n;
    }

    private static int sequenceList(CommandSourceStack src) {
        var all = services().registry().sequences().keySet();
        var running = services().schedule().runningSequences();
        src.sendSuccess(() -> Component.translatable("liveevents.sequence.list", all.size(), String.join(", ", running)), false);
        all.forEach(id -> src.sendSuccess(() -> Component.literal(" • " + id).withStyle(ChatFormatting.GRAY), false));
        return all.size();
    }

    // ================================================================================================ panel

    /** Abre el panel de administración: solo envía la lista de eventos; el panel manda comandos normales. */
    private static int gui(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        JsonObject root = new JsonObject();
        JsonArray events = new JsonArray();
        for (EventDefinition d : services().registry().definitions().values()) {
            if (!Perms.canStart(src, d.id())) {
                continue;
            }
            JsonObject e = new JsonObject();
            e.addProperty("id", d.id());
            e.addProperty("name", d.displayName());
            e.addProperty("description", d.description());
            e.addProperty("scope", d.scope().id());
            e.addProperty("duration", Durations.ticksToSeconds(d.durationTicks()));
            JsonArray phases = new JsonArray();
            for (PhaseDef p : d.phases()) {
                phases.add(p.id());
            }
            e.add("phases", phases);
            events.add(e);
        }
        root.add("events", events);
        JsonArray active = new JsonArray();
        for (EventInstance i : services().director().active()) {
            JsonObject a = new JsonObject();
            a.addProperty("id", i.instanceId());
            a.addProperty("event", i.definitionId());
            a.addProperty("name", i.definition().displayName());
            a.addProperty("elapsed", i.elapsedSeconds());
            a.addProperty("duration", i.durationSeconds());
            a.addProperty("paused", i.paused());
            active.add(a);
        }
        root.add("active", active);
        root.addProperty("can_panic", Perms.check(src, Perms.PANIC, OP2));
        root.addProperty("can_stop", Perms.check(src, Perms.STOP, OP2));
        root.addProperty("can_control", Perms.check(src, Perms.CONTROL, OP2));
        String json = root.toString();
        if (json.length() > Payloads.MAX_JSON) {
            src.sendFailure(Component.translatable("liveevents.error.gui_too_big"));
            return 0;
        }
        if (!ServerNet.hasMod(player)) {
            src.sendFailure(Component.translatable("liveevents.error.client_missing"));
            return 0;
        }
        ServerNet.send(player, new Payloads.OpenGui(json));
        return 1;
    }

    private static String trim(double v) {
        return v == Math.floor(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }
}
