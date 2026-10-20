package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.couchcraft.context.Couch;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Runs on the integrated server thread. A couch guest logs in with their profile's fixed ID instead of one derived
 * from their name, so the world keeps their inventory, XP, advancements and spawn when they rename the profile.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {
	@Shadow
	@Final
	private Connection connection;

	@WrapOperation(
		method = "handleHello",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/core/UUIDUtil;createOfflineProfile(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;")
	)
	private GameProfile couchcraft$profileId(final String name, final Operation<GameProfile> original) {
		UUID id = this.connection.isMemoryConnection() ? Couch.guestId(name) : null;
		return id != null ? new GameProfile(id, name) : original.call(name);
	}
}
