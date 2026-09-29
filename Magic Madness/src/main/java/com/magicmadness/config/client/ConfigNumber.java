package com.magicmadness.config.client;

// #region 1. IMPORTS
import com.magicmadness.config.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
// #endregion

// ============================================================================
// MAGIC MADNESS — SINGLE CONFIG NUMBER / TOGGLE / CHOICE MODEL
// ============================================================================
public record ConfigNumber(Component label, Component description, Unit unit, double min, double max, double step,
                           boolean whole, double defaultValue, DoubleSupplier stored, DoubleConsumer store,
                           String file, List<String> path, @Nullable String choices) {

    // #region 2. CONSTRUCTORS & VALUE FORMATTING
    public ConfigNumber(Component label, Component description, Unit unit, double min, double max, double step,
                        boolean whole, double defaultValue, DoubleSupplier stored, DoubleConsumer store,
                        String file, List<String> path) {
        this(label, description, unit, min, max, step, whole, defaultValue, stored, store, file, path, null);
    }

    public Component meaning(double value) {
        return this.choices != null
                ? Component.translatable(this.choices + "." + Math.round(value))
                : this.unit.describe(value);
    }

    public String format(double value) {
        if (this.whole) {
            return Long.toString(Math.round(value));
        }
        return BigDecimal.valueOf(Math.round(value * 1000.0) / 1000.0).stripTrailingZeros().toPlainString();
    }

    public double clamp(double value) {
        double kept = Math.max(this.min, Math.min(this.max, value));
        return this.whole ? Math.round(kept) : kept;
    }
    // #endregion
}
