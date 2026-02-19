package eu.mikart.kimmoke.server;

public record Position(double x, double y, double z, float yaw, float pitch) {
    public static Position ZERO = new Position(0, 0, 0);

    public Position(double x, double y, double z) {
        this(x, y, z, 0, 0);
    }

    public Position sub(double x, double y, double z) {
        return new Position(this.x - x, this.y - y, this.z - z, yaw, pitch);
    }
}
