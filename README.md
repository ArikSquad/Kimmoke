# Kimmoke

Lightweight, high-performance, and low memory-footprint Minecraft 1.21.11 Limbo server implementation.

- Java NIO **without Netty**
- No external dependencies (for the server itself)
- Supports modern Velocity proxy authentication
- Optimized for low latency and high throughput

## Updating to a new version

First, you'll need to update the protocol packet ids, you can get these from these sources:

- https://raw.githubusercontent.com/PrismarineJS/minecraft-data/refs/heads/master/data/pc/1.21.11/protocol.json
- https://minecraft.wiki/w/Java_Edition_protocol/Packets

Then, you'll also need new codec and tag nbt files in src/main/resources/