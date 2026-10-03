package com.takumistudios.liveevents.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Paleta de transformación de bloques ({@code data/<ns>/liveevents/palette/<id>.json}). Reglas en orden: la primera
 * cuyo "from" coincide gana; si varias reglas seguidas tienen el mismo "from", se elige entre ellas por peso.
 * <pre>{@code {"format_version": 1, "rules": [{"from": "#minecraft:leaves", "to": "minecraft:air"}]}}</pre>
 */
public final class BlockPalette {
    private record Rule(Block block, TagKey<Block> tag, boolean any, BlockState to, int weight) {
        boolean matches(BlockState state) {
            if (any) {
                return !state.isAir();
            }
            if (tag != null) {
                return state.is(tag);
            }
            return state.getBlock() == block;
        }

        String fromKey() {
            return any ? "*" : tag != null ? "#" + tag.location() : BuiltInRegistries.BLOCK.getKey(block).toString();
        }
    }

    private final String id;
    private final List<Rule> rules;

    private BlockPalette(String id, List<Rule> rules) {
        this.id = id;
        this.rules = rules;
    }

    public String id() {
        return id;
    }

    public static BlockPalette parse(String id, JsonObject json, RegistryAccess access) {
        List<Rule> rules = new ArrayList<>();
        if (!json.has("rules") || !json.get("rules").isJsonArray()) {
            throw new IllegalArgumentException("falta \"rules\"");
        }
        for (JsonElement e : json.getAsJsonArray("rules")) {
            JsonObject r = e.getAsJsonObject();
            String from = r.get("from").getAsString();
            String to = r.get("to").getAsString();
            int weight = r.has("weight") ? Math.max(1, r.get("weight").getAsInt()) : 1;
            Block toBlock = block(to);
            if (from.equals("*")) {
                rules.add(new Rule(null, null, true, toBlock.defaultBlockState(), weight));
            } else if (from.startsWith("#")) {
                rules.add(new Rule(null, TagKey.create(Registries.BLOCK, Identifier.parse(from.substring(1))), false, toBlock.defaultBlockState(), weight));
            } else {
                rules.add(new Rule(block(from), null, false, toBlock.defaultBlockState(), weight));
            }
        }
        if (rules.size() > 512) {
            throw new IllegalArgumentException("demasiadas reglas (máximo 512)");
        }
        return new BlockPalette(id, List.copyOf(rules));
    }

    private static Block block(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalArgumentException("bloque desconocido: " + id);
        }
        return BuiltInRegistries.BLOCK.getValue(key);
    }

    /** Estado de destino para un bloque, o vacío si ninguna regla lo cambia. */
    public Optional<BlockState> apply(BlockState state, DeterministicRandom random) {
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (!rule.matches(state)) {
                continue;
            }
            // Grupo de reglas consecutivas con el mismo "from": elegir por peso
            int end = i;
            int total = 0;
            while (end < rules.size() && rules.get(end).fromKey().equals(rule.fromKey())) {
                total += rules.get(end).weight();
                end++;
            }
            int pick = random.nextInt(total);
            for (int j = i; j < end; j++) {
                pick -= rules.get(j).weight();
                if (pick < 0) {
                    BlockState to = rules.get(j).to();
                    return to == state ? Optional.empty() : Optional.of(to);
                }
            }
        }
        return Optional.empty();
    }
}
