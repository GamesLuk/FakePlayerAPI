package de.gamesluk.fakeplayerapi;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.gamesluk.fakeplayerapi.api.FakePlayerAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.util.List;

public class Main implements ModInitializer {
    public static boolean DEBUG = false;

    public static boolean isDebugEnabled() {
        return DEBUG;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("fakeplayerapi")
                .requires(source -> Permissions.check(source, "gamescapes.developer.fakeplayerapi", 2))
                
                // Spawn Subcommand
                .then(Commands.literal("spawn")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            var source = context.getSource();
                            var server = source.getServer();

                            String name = StringArgumentType.getString(context, "name");

                            boolean success = FakePlayerAPI.spawnFakePlayer(server, name);

                            if (success) {
                                source.sendSuccess(() -> Component.literal("Fake-Spieler " + name + " erfolgreich gespawnt!"), false);
                            } else {
                                source.sendFailure(Component.literal("Fehler beim Spawnen. Ist der Spieler schon da?"));
                            }

                            return 1;
                        })
                    )
                )

                // Delete Subcommand
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            var server = context.getSource().getServer();
                            List<String> fakePlayers = server.getPlayerList().getPlayers().stream()
                                    .filter(p -> FakePlayerAPI.isFake(server, p.getScoreboardName()))
                                    .map(ServerPlayer::getScoreboardName)
                                    .toList();
                            return SharedSuggestionProvider.suggest(fakePlayers, builder);
                        })
                        .executes(context -> {
                            var source = context.getSource();
                            var server = source.getServer();
                            String name = StringArgumentType.getString(context, "name");

                            boolean success = FakePlayerAPI.deleteFakePlayer(server, name);
                            if (success) {
                                source.sendSuccess(() -> Component.literal("Fake-Spieler " + name + " wurde entfernt."), false);
                                return 1;
                            }

                            source.sendFailure(Component.literal("Konnte Fake-Spieler " + name + " nicht entfernen (nicht online, kein Fake oder gerade am Spawnen)."));
                            return 0;
                        })
                    )
                )

                // Check Subcommand
                .then(Commands.literal("check")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            var server = context.getSource().getServer();
                            return SharedSuggestionProvider.suggest(server.getPlayerNames(), builder);
                        })
                        .executes(context -> {
                            var source = context.getSource();
                            var server = source.getServer();
                            String name = StringArgumentType.getString(context, "name");

                            boolean isFake = FakePlayerAPI.isFake(server, name);
                            if (isFake) {
                                source.sendSuccess(() -> Component.literal(name + " ist ein Fake-Spieler!"), false);
                            } else {
                                source.sendSuccess(() -> Component.literal(name + " ist KEIN Fake-Spieler (oder nicht online)."), false);
                            }

                            return 1;
                        })
                    )
                )
            );
        });
    }
}
