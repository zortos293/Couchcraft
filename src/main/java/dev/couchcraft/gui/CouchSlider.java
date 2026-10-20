package dev.couchcraft.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A slider over a {@code [min, max]} range that writes straight into a config field. */
final class CouchSlider extends AbstractSliderButton {
	private final Component name;
	private final double min;
	private final double max;
	private final DoubleConsumer setter;
	private final DoubleFunction<String> format;

	CouchSlider(
		final Component name, final double min, final double max, final DoubleSupplier getter, final DoubleConsumer setter, final DoubleFunction<String> format
	) {
		super(0, 0, 150, 20, Component.empty(), (Math.clamp(getter.getAsDouble(), min, max) - min) / (max - min));
		this.name = name;
		this.min = min;
		this.max = max;
		this.setter = setter;
		this.format = format;
		this.updateMessage();
	}

	private double current() {
		return this.min + this.value * (this.max - this.min);
	}

	@Override
	protected void updateMessage() {
		this.setMessage(CommonComponents.optionNameValue(this.name, Component.literal(this.format.apply(this.current()))));
	}

	@Override
	protected void applyValue() {
		this.setter.accept(this.current());
	}
}
