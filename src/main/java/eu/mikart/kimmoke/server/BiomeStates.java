package eu.mikart.kimmoke.server;

import java.util.HashMap;
import java.util.Map;

final class BiomeStates {
    static final int DEFAULT_BIOME_ID = 40;
    static final Map<String, Integer> BIOME_IDS = loadBiomeIds();
    private BiomeStates() {
    }

    private static Map<String, Integer> loadBiomeIds() {
        Map<String, Integer> map = new HashMap<>(70);
        map.put("minecraft:badlands", 0);
        map.put("minecraft:bamboo_jungle", 1);
        map.put("minecraft:basalt_deltas", 2);
        map.put("minecraft:beach", 3);
        map.put("minecraft:birch_forest", 4);
        map.put("minecraft:cherry_grove", 5);
        map.put("minecraft:cold_ocean", 6);
        map.put("minecraft:crimson_forest", 7);
        map.put("minecraft:dark_forest", 8);
        map.put("minecraft:deep_cold_ocean", 9);
        map.put("minecraft:deep_dark", 10);
        map.put("minecraft:deep_frozen_ocean", 11);
        map.put("minecraft:deep_lukewarm_ocean", 12);
        map.put("minecraft:deep_ocean", 13);
        map.put("minecraft:desert", 14);
        map.put("minecraft:dripstone_caves", 15);
        map.put("minecraft:end_barrens", 16);
        map.put("minecraft:end_highlands", 17);
        map.put("minecraft:end_midlands", 18);
        map.put("minecraft:eroded_badlands", 19);
        map.put("minecraft:flower_forest", 20);
        map.put("minecraft:forest", 21);
        map.put("minecraft:frozen_ocean", 22);
        map.put("minecraft:frozen_peaks", 23);
        map.put("minecraft:frozen_river", 24);
        map.put("minecraft:grove", 25);
        map.put("minecraft:ice_spikes", 26);
        map.put("minecraft:jagged_peaks", 27);
        map.put("minecraft:jungle", 28);
        map.put("minecraft:lukewarm_ocean", 29);
        map.put("minecraft:lush_caves", 30);
        map.put("minecraft:mangrove_swamp", 31);
        map.put("minecraft:meadow", 32);
        map.put("minecraft:mushroom_fields", 33);
        map.put("minecraft:nether_wastes", 34);
        map.put("minecraft:ocean", 35);
        map.put("minecraft:old_growth_birch_forest", 36);
        map.put("minecraft:old_growth_pine_taiga", 37);
        map.put("minecraft:old_growth_spruce_taiga", 38);
        map.put("minecraft:pale_garden", 39);
        map.put("minecraft:plains", 40);
        map.put("minecraft:river", 41);
        map.put("minecraft:savanna", 42);
        map.put("minecraft:savanna_plateau", 43);
        map.put("minecraft:small_end_islands", 44);
        map.put("minecraft:snowy_beach", 45);
        map.put("minecraft:snowy_plains", 46);
        map.put("minecraft:snowy_slopes", 47);
        map.put("minecraft:snowy_taiga", 48);
        map.put("minecraft:soul_sand_valley", 49);
        map.put("minecraft:sparse_jungle", 50);
        map.put("minecraft:stony_peaks", 51);
        map.put("minecraft:stony_shore", 52);
        map.put("minecraft:sunflower_plains", 53);
        map.put("minecraft:swamp", 54);
        map.put("minecraft:taiga", 55);
        map.put("minecraft:the_end", 56);
        map.put("minecraft:the_void", 57);
        map.put("minecraft:warm_ocean", 58);
        map.put("minecraft:warped_forest", 59);
        map.put("minecraft:windswept_forest", 60);
        map.put("minecraft:windswept_gravelly_hills", 61);
        map.put("minecraft:windswept_hills", 62);
        map.put("minecraft:windswept_savanna", 63);
        map.put("minecraft:wooded_badlands", 64);
        return Map.copyOf(map);
    }
}
