package eu.mikart.kimmoke;

import de.exlll.configlib.Configuration;

@Configuration
@SuppressWarnings("FieldMayBeFinal")
public class Settings {
    private int port = 25565;
    private String worldPath = "limbo.polar";
    private boolean velocityModernForwarding = false;
    private String velocitySecret = "";

    public int getPort() {
        return port;
    }

    public String getWorldPath() {
        return worldPath;
    }

    public boolean isVelocityModernForwarding() {
        return velocityModernForwarding;
    }

    public String getVelocitySecret() {
        return velocitySecret;
    }
}
