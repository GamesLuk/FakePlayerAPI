package de.gamesluk.fakeplayerapi.integration;

import de.gamesluk.fakeplayerapi.Main;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Soft-dependency on LuckPerms: pre-loads user data before the fake login,
 * without requiring a hard compile dependency on the LuckPerms API artifact.
 */
public final class LuckPermsPreloadBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsPreloadBridge.class);
    private static final String DEBUG_PREFIX = "[FakePlayerDebug][LuckPerms]";

    private LuckPermsPreloadBridge() {
    }

    public static void preloadUserIfAvailable(UUID uniqueId, String username) {
        long startNanos = System.nanoTime();
        logInfo("preload start; username={}, uuid={}", username, uniqueId);

        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            logInfo("mod not loaded -> skip preload");
            return;
        }

        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);

            CompletionStage<?> loadStage = invokeLoadUser(userManager, uniqueId, username);
            if (loadStage != null) {
                loadStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
                logInfo("loadUser completed in {} ms", (System.nanoTime() - startNanos) / 1_000_000L);
            } else {
                logWarn("no CompletionStage returned by loadUser reflection path");
            }
        } catch (Throwable t) {
            logWarn("preload failed for {} ({}) after {} ms", username, uniqueId, (System.nanoTime() - startNanos) / 1_000_000L, t);
        }
    }

    private static CompletionStage<?> invokeLoadUser(Object userManager, UUID uniqueId, String username) throws Exception {
        Method loadWithName = findMethod(userManager.getClass(), "loadUser", UUID.class, String.class);
        if (loadWithName != null) {
            Object result = loadWithName.invoke(userManager, uniqueId, username);
            if (result instanceof CompletionStage<?> stage) {
                return stage;
            }
            logWarn("loadUser(UUID,String) returned non-CompletionStage: {}", result == null ? "null" : result.getClass().getName());
        }

        Method loadByUuid = findMethod(userManager.getClass(), "loadUser", UUID.class);
        if (loadByUuid != null) {
            Object result = loadByUuid.invoke(userManager, uniqueId);
            if (result instanceof CompletionStage<?> stage) {
                return stage;
            }
            logWarn("loadUser(UUID) returned non-CompletionStage: {}", result == null ? "null" : result.getClass().getName());
        }

        logWarn("no compatible loadUser method found on {}", userManager.getClass().getName());

        return CompletableFuture.completedFuture(null);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void logInfo(String message, Object... args) {
        if (Main.isDebugEnabled()) {
            LOGGER.info(DEBUG_PREFIX + " " + message, args);
        }
    }

    private static void logWarn(String message, Object... args) {
        if (Main.isDebugEnabled()) {
            LOGGER.warn(DEBUG_PREFIX + " " + message, args);
        }
    }
}
