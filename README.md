# FakePlayerAPI

This mod provides an API to spawn and manage fake players on Fabric servers. Upon spawning, fake players behave passively in a "superposition" (they are neither ticked nor loaded into the world), but they appear fully functional in the tab list and are correctly recognized for permissions by plugins like LuckPerms.

## Code Origin (Credits)
The internal core logic for spawning fake players (`EntityPlayerMPFake.java`, etc.) is largely based on the code from the excellent [Carpet Mod](https://github.com/gnembon/fabric-carpet) by gnembon. 
The modifications, decoupling from the movement system, and adaptations for pure API usage were done separately. 
More information regarding the terms of gnembon's original code can be found in the attached [`CARPET_LICENSE.txt`](CARPET_LICENSE.txt) (MIT License).

## Optional Integration into Other Mods (Soft-Dependency)

If you want to use this API in another mod **without** making it a hard dependency in your `build.gradle`, you can use **Java Reflection**.

This turns the FakePlayerAPI into a purely optional extension: If a server owner installs your mod alongside the FakePlayerAPI, the features can be used. If the FakePlayerAPI is missing, your server will continue to run at 100% functionality without any crashes.

### Example for a Bridge Class (Reflection)

Simply create a utility class in your other project and use it to safely access the FakePlayerAPI:

```java
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public class FakePlayerBridge {

    // Checks once if the mod is present on the server
    private static final boolean IS_LOADED = FabricLoader.getInstance().isModLoaded("fakeplayerapi");

    public static boolean spawnFake(MinecraftServer server, String username) {
        if (!IS_LOADED) return false;
        try {
            Class<?> apiClass = Class.forName("de.gamesluk.fakeplayerapi.api.FakePlayerAPI");
            Method method = apiClass.getMethod("spawnFakePlayer", MinecraftServer.class, String.class);
            return (boolean) method.invoke(null, server, username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteFake(MinecraftServer server, String username) {
        if (!IS_LOADED) return false;
        try {
            Class<?> apiClass = Class.forName("de.gamesluk.fakeplayerapi.api.FakePlayerAPI");
            Method method = apiClass.getMethod("deleteFakePlayer", MinecraftServer.class, String.class);
            return (boolean) method.invoke(null, server, username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isFake(MinecraftServer server, String username) {
        if (!IS_LOADED) return false;
        try {
            Class<?> apiClass = Class.forName("de.gamesluk.fakeplayerapi.api.FakePlayerAPI");
            Method method = apiClass.getMethod("isFake", MinecraftServer.class, String.class);
            return (boolean) method.invoke(null, server, username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
```
