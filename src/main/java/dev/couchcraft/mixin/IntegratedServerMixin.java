package dev.couchcraft.mixin;

import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs on the server thread. If the world allows commands, couch guests may use them like the host. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestsAreFamily(final NameAndId nameAndId, final CallbackInfoReturnable<LevelBasedPermissionSet> cir) {
		if (CouchConfig.get().guestsInheritCheats
			&& Couch.isGuestName(nameAndId.name())
			&& ((IntegratedServer)(Object)this).getWorldData().isAllowCommands()) {
			cir.setReturnValue(LevelBasedPermissionSet.GAMEMASTER);
		}
	}
}
