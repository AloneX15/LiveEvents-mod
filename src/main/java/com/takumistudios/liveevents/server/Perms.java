package com.takumistudios.liveevents.server;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/**
 * Nodos de permiso (§10) sobre la API de permisos de Fabric. Si hay un gestor de permisos (LuckPerms u otro), decide
 * él; si no, se usa el nivel de OP de respaldo. Los nodos tienen la forma {@code liveevents:start.supernova}.
 */
public final class Perms {
    public static final Identifier COMMAND = node("command");
    public static final Identifier LIST = node("list");
    public static final Identifier START = node("start");
    public static final Identifier START_ANYWHERE = node("start.anywhere");
    public static final Identifier CONTROL = node("control");
    public static final Identifier STOP = node("stop");
    public static final Identifier REVERT = node("revert");
    public static final Identifier EDIT = node("edit");
    public static final Identifier RELOAD = node("reload");
    public static final Identifier PREVIEW = node("preview");
    public static final Identifier BYPASS_DAMAGE = node("bypass.damage");
    public static final Identifier BYPASS_VISUALS = node("bypass.visuals");
    public static final Identifier NOTIFY = node("notify");
    public static final Identifier CREATE = node("create");
    public static final Identifier VALIDATE = node("validate");
    public static final Identifier DEBUG = node("debug");
    public static final Identifier PANIC = node("panic");
    public static final Identifier SCHEDULE = node("schedule");
    public static final Identifier GUI = node("gui");

    private Perms() {
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("liveevents", path);
    }

    /** Nodo creado automáticamente para cada evento: liveevents:start.&lt;ns&gt;.&lt;evento&gt; (oficiales: start.&lt;evento&gt;). */
    public static Identifier startNode(String eventId) {
        int colon = eventId.indexOf(':');
        String ns = eventId.substring(0, colon), path = eventId.substring(colon + 1).replace('/', '.');
        return node(ns.equals("liveevents") ? "start." + path : "start." + ns + "." + path);
    }

    public static boolean check(CommandSourceStack source, Identifier node, PermissionLevel fallback) {
        return ((PermissionContextOwner) source).checkPermission(node, fallback);
    }

    public static boolean check(ServerPlayer player, Identifier node, PermissionLevel fallback) {
        return ((PermissionContextOwner) player).checkPermission(node, fallback);
    }

    /** Requisito para Brigadier. */
    public static Predicate<CommandSourceStack> require(Identifier node, PermissionLevel fallback) {
        return source -> check(source, node, fallback);
    }

    /** Lanzar un evento concreto: el nodo general o el del evento. */
    public static boolean canStart(CommandSourceStack source, String eventId) {
        return check(source, START, PermissionLevel.GAMEMASTERS) || check(source, startNode(eventId), PermissionLevel.GAMEMASTERS);
    }

    /** Inmune al daño y a los efectos de estado de los eventos. Sin gestor de permisos: nadie lo es por defecto. */
    public static boolean bypassDamage(ServerPlayer player) {
        return ((PermissionContextOwner) player).checkPermission(BYPASS_DAMAGE, false);
    }
}
