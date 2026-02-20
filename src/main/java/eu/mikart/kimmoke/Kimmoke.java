package eu.mikart.kimmoke;

import de.exlll.configlib.YamlConfigurations;
import eu.mikart.kimmoke.server.NioLimboServer;
import lombok.Getter;

import java.nio.file.Path;

public class Kimmoke {
    @Getter
    private static Settings settings;

    static void main() throws Exception {
        settings = YamlConfigurations.update(Path.of("config.yml"), Settings.class);

        var server = new NioLimboServer(
            "0.0.0.0",
            settings.getPort(),
            settings.isVelocityModernForwarding(),
            settings.getVelocitySecret(),
            false,
            settings.getSpawnPosition()
        );
        server.run();
    }
}
