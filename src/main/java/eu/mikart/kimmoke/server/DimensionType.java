package eu.mikart.kimmoke.server;

import lombok.Getter;
import net.kyori.adventure.key.Key;

@Getter
public enum DimensionType {
    OVERWORLD(Key.key("overworld")),
    THE_NETHER(Key.key("the_nether")),
    THE_END(Key.key("the_end"));

    private final Key id;

    DimensionType(Key id) {
        this.id = id;
    }
}
