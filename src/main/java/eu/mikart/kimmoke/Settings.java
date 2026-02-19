package eu.mikart.kimmoke;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import eu.mikart.kimmoke.server.DimensionType;
import eu.mikart.kimmoke.server.Position;
import lombok.Getter;

@Getter
@Configuration
@SuppressWarnings("FieldMayBeFinal")
public class Settings {
    @Comment("The port to listen on")
    private int port = 25565;
    @Comment("Whether to load the world from a polar file")
    private boolean polar = false;
    @Comment("The path to the world to load")
    private String worldPath = "limbo.polar";
    @Comment("Whether to enable Velocity modern forwarding")
    private boolean velocityModernForwarding = false;
    @Comment("The secret for Velocity modern forwarding")
    private String velocitySecret = "";
    @Comment("The dimension to display for the player")
    private DimensionType dimension = DimensionType.OVERWORLD;
    @Comment("Boolean hardcore in Login (play) packet")
    private boolean hardcore = true;
    @Comment("Spawn position for the player")
    private Position spawnPosition = Position.ZERO;
}
