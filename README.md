# FakePlayerAPI

Diese Mod bietet eine API zum Spawnen und Verwalten von Fake-Spielern auf Fabric-Servern. Die Fake-Spieler verhalten sich nach dem Spawnen passiv in einer "Superposition" (werden nicht getickt oder in die Welt geladen), erscheinen aber voll funktionsfähig in der Tabliste und werden auch von z.B. LuckPerms korrekt für Berechtigungen erkannt.

## Optionale Einbindung in andere Mods (Soft-Dependency)

Wenn du diese API in einer anderen Mod verwenden möchtest, **ohne** sie als feste Abhängigkeit in deiner `build.gradle` einzutragen, kannst du **Java Reflection** nutzen.

Dadurch wird die FakePlayerAPI zu einer reinen optionalen Erweiterung: Wenn ein Server-Besitzer deine Mod und zusätzlich die FakePlayerAPI installiert, können die Funktionen genutzt werden. Wenn die FakePlayerAPI fehlt, läuft dein Server trotzdem zu 100% ohne Crashes weiter.

### Beispiel für eine Bridge-Klasse (Reflection)

Erstelle einfach eine Utility-Klasse in deinem anderen Projekt und nutze diese, um sicher auf die FakePlayerAPI zuzugreifen:

```java
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public class FakePlayerBridge {

    // Prüft einmalig, ob die Mod auf dem Server vorhanden ist
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

