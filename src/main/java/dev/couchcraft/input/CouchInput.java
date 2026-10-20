package dev.couchcraft.input;

import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Keyboard movement plus true analog movement from the player's left stick. Installed for every local player,
 * so a single keyboard player is unaffected and a controller player walks at any speed between sneak and run.
 */
public final class CouchInput extends KeyboardInput {
	private static final float DIGITAL_THRESHOLD = 0.3F;

	public CouchInput(final Options options) {
		super(options);
	}

	@Override
	public void tick() {
		super.tick();
		PlayerContext context = Couch.current();
		if (context == null || context.pad == null || Minecraft.getInstance().gui.screen() != null) {
			return;
		}

		float[] stick = PadMath.radial(context.pad.leftX(), context.pad.leftY(), CouchConfig.get().stickDeadzone);
		float left = -stick[0];
		float forward = -stick[1];
		if (left == 0.0F && forward == 0.0F) {
			return;
		}

		Input keys = this.keyPresses;
		this.keyPresses = new Input(
			keys.forward() || forward > DIGITAL_THRESHOLD,
			keys.backward() || forward < -DIGITAL_THRESHOLD,
			keys.left() || left > DIGITAL_THRESHOLD,
			keys.right() || left < -DIGITAL_THRESHOLD,
			keys.jump(),
			keys.shift(),
			keys.sprint()
		);
		this.moveVector = new Vec2(left, forward);
	}
}
