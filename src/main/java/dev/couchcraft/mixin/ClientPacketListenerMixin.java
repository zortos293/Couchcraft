package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.couchcraft.context.Couch;
import dev.couchcraft.input.CouchInput;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Every local player gets keyboard + analog controller movement. */
	@ModifyExpressionValue(
		method = {"handleLogin", "handleRespawn"},
		at = @At(value = "NEW", target = "(Lnet/minecraft/client/Options;)Lnet/minecraft/client/player/KeyboardInput;")
	)
	private KeyboardInput couchcraft$analogInput(final KeyboardInput original) {
		return new CouchInput(Minecraft.getInstance().options);
	}

	/** The integrated server's owner is the host; a guest logging in must not take that over. */
	@WrapWithCondition(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;setUUID(Ljava/util/UUID;)V"))
	private boolean couchcraft$hostStaysOwner(final IntegratedServer instance, final UUID uuid) {
		return !Couch.isGuestActive();
	}

	/** A guest changing dimension must not cut the host's music. */
	@WrapWithCondition(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/MusicManager;stopPlaying()V"))
	private boolean couchcraft$guestKeepsMusic(final MusicManager instance) {
		return !Couch.isGuestActive();
	}
}
