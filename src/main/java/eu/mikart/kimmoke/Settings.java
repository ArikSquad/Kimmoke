package eu.mikart.kimmoke;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import eu.mikart.kimmoke.server.Position;
import lombok.Getter;

@Getter
@Configuration
@SuppressWarnings("FieldMayBeFinal")
public class Settings {
    @Comment("The port to listen on")
    private int port = 25565;
    @Comment("Whether to enable Velocity modern forwarding")
    private boolean velocityModernForwarding = false;
    @Comment("The secret for Velocity modern forwarding")
    private String velocitySecret = "";
    @Comment("Spawn position for the player")
    private Position spawnPosition = Position.ZERO;
}
