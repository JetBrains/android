/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.configurations;

import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.Locale;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@linkplain NestedConfiguration} is a {@link Configuration} which inherits
 * all of its values from a different configuration, except for one or more
 * attributes where it overrides a custom value.
 * <p/>
 * Unlike a {@link VaryingConfiguration}, a {@linkplain NestedConfiguration}
 * will always return the same overridden value, regardless of the inherited
 * value.
 * <p/>
 * For example, an {@linkplain NestedConfiguration} may fix the locale to always
 * be "en", but otherwise inherit everything else.
 */
public class NestedConfiguration extends Configuration implements ConfigurationListener {
  /**
   * The configuration we are inheriting non-overridden values from
   */
  protected Configuration myParent;

  /**
   * Bitmask of attributes to be overridden in this configuration
   */
  private int myOverride;

  /**
   * Constructs a new {@linkplain NestedConfiguration}.
   * Construct via {@link #create(Configuration)}.
   *
   * @param configuration the configuration to inherit from
   */
  protected NestedConfiguration(@NotNull Configuration configuration) {
    super(configuration.getConfigurationManager(), configuration.getFile(), configuration.getEditedConfig());
    myParent = configuration;
    myFullConfig.set(myParent.myFullConfig);

    myParent.addListener(this);
  }

  /**
   * Returns the override flags for this configuration. Corresponds to
   * the {@code CFG_} flags in {@link ConfigurationListener}.
   *
   * @return the bitmask
   */
  public int getOverrideFlags() {
    return myOverride;
  }

  /**
   * Creates a new {@linkplain NestedConfiguration} that has the same overriding
   * attributes as the given other {@linkplain NestedConfiguration}, and gets
   * its values from the given {@linkplain Configuration}.
   *
   * @param other  the configuration to copy overrides from
   * @param values the configuration to copy values from
   * @param parent the parent to tie the configuration to for inheriting values
   * @return a new configuration
   */
  @NotNull
  public static NestedConfiguration create(@NotNull NestedConfiguration other,
                                           @NotNull Configuration values,
                                           @NotNull Configuration parent) {
    NestedConfiguration configuration = new NestedConfiguration(parent);
    initFrom(configuration, other, values);
    return configuration;
  }

  /**
   * Initializes a new {@linkplain NestedConfiguration} with the overriding
   * attributes as the given other {@linkplain NestedConfiguration}, and gets
   * its values from the given {@linkplain Configuration}.
   *
   * @param configuration the configuration to initialize
   * @param other         the configuration to copy overrides from
   * @param values        the configuration to copy values from
   */
  protected static void initFrom(NestedConfiguration configuration, NestedConfiguration other, Configuration values) {
    // TODO: Rewrite to use the clone method!
    configuration.startBulkEditing();
    configuration.myOverride = other.myOverride;
    configuration.setDisplayName(values.getDisplayName());
    String activity = values.getActivity();
    if (activity != null) {
      configuration.setActivity(activity);
    }

    if (configuration.isOverridingLocale()) {
      configuration.setLocale(values.getLocale());
    }
    if (configuration.isOverridingTarget()) {
      IAndroidTarget target = values.getTarget();
      if (target != null) {
        configuration.setTarget(target);
      }
    }
    if (configuration.isOverridingDevice()) {
      Device device = values.getDevice();
      if (device != null) {
        configuration.setDevice(device, true);
      }
    }
    if (configuration.isOverridingDeviceState()) {
      State deviceState = values.getDeviceState();
      if (deviceState != null) {
        configuration.setDeviceState(deviceState);
      }
    }
    if (configuration.isOverridingNightMode()) {
      configuration.setNightMode(values.getNightMode());
    }
    if (configuration.isOverridingUiMode()) {
      configuration.setUiMode(values.getUiMode());
    }
    configuration.finishBulkEditing();
  }

  /**
   * Sets the parent configuration that this configuration is inheriting from.
   *
   * @param parent the parent configuration
   */
  public void setParent(@NotNull Configuration parent) {
    myParent = parent;
  }

  /**
   * Creates a new {@linkplain Configuration} which inherits values from the
   * given parent {@linkplain Configuration}, possibly overriding some as
   * well.
   *
   * @param parent  the configuration to inherit values from
   * @return a new configuration
   */
  @NotNull
  public static NestedConfiguration create(@NotNull Configuration parent) {
    return new NestedConfiguration(parent);
  }

  @Override
  @NotNull
  public String getTheme() {
    if (isOverridingTarget()) {
      return super.getTheme();
    } else {
      return myParent.getTheme();
    }
  }

  @Override
  public void setTheme(@Nullable String theme) {
    if (isOverridingTarget()) {
      super.setTheme(theme);
    } else {
      myParent.setTheme(theme);
    }
  }

  /**
   * Sets whether the locale should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideLocale(boolean override) {
    if (override) {
      myOverride |= CFG_LOCALE;
    } else {
      myOverride &= ~CFG_LOCALE;
    }
  }

  /**
   * Returns true if the locale is overridden
   *
   * @return true if the locale is overridden
   */
  public final boolean isOverridingLocale() {
    return (myOverride & CFG_LOCALE) != 0;
  }

  @Override
  @NotNull
  public Locale getLocale() {
    if (isOverridingLocale()) {
      return super.getLocale();
    }
    else {
      return myParent.getLocale();
    }
  }

  @Override
  public void setLocale(@NotNull Locale locale) {
    if (isOverridingLocale()) {
      super.setLocale(locale);
    }
    else {
      myParent.setLocale(locale);
    }
  }

  /**
   * Sets whether the rendering target should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideTarget(boolean override) {
    if (override) {
      myOverride |= CFG_TARGET;
    } else {
      myOverride &= ~CFG_TARGET;
    }
  }

  /**
   * Returns true if the target is overridden
   *
   * @return true if the target is overridden
   */
  public final boolean isOverridingTarget() {
    return (myOverride & CFG_TARGET) != 0;
  }

  @Override
  @Nullable
  public IAndroidTarget getTarget() {
    if (isOverridingTarget()) {
      return super.getTarget();
    }
    else {
      return myParent.getTarget();
    }
  }

  @Override
  public void setTarget(IAndroidTarget target) {
    if (isOverridingTarget()) {
      super.setTarget(target);
    }
    else {
      myParent.setTarget(target);
    }
  }

  /**
   * Sets whether the device should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideDevice(boolean override) {
    if (override) {
      myOverride |= CFG_DEVICE;
    } else {
      myOverride &= ~CFG_DEVICE;
    }
  }

  /**
   * Returns true if the device is overridden
   *
   * @return true if the device is overridden
   */
  public final boolean isOverridingDevice() {
    return (myOverride & CFG_DEVICE) != 0;
  }

  @Override
  @Nullable
  public Device getDevice() {
    if (isOverridingDevice()) {
      return super.getDevice();
    }
    else {
      return myParent.getDevice();
    }
  }

  @Override
  public void setDevice(Device device, boolean preserveState) {
    if (isOverridingDevice()) {
      super.setDevice(device, preserveState);
    }
    else {
      myParent.setDevice(device, preserveState);
    }
  }

  /**
   * Sets whether the device state should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideDeviceState(boolean override) {
    if (override) {
      myOverride |= CFG_DEVICE_STATE;
    } else {
      myOverride &= ~CFG_DEVICE_STATE;
    }
  }

  /**
   * Returns true if the device state is overridden
   *
   * @return true if the device state is overridden
   */
  public final boolean isOverridingDeviceState() {
    return (myOverride & CFG_DEVICE_STATE) != 0;
  }

  @Override
  @Nullable
  public State getDeviceState() {
    if (isOverridingDeviceState()) {
      return super.getDeviceState();
    }
    else {
      State state = myParent.getDeviceState();
      if (isOverridingDevice()) {
        // If the device differs, I need to look up a suitable equivalent state
        // on our device
        if (state != null) {
          Device device = super.getDevice();
          if (device != null) {
            String name = state.getName();
            state = device.getState(name);
            if (state != null) {
              return state;
            }
            // No such state in this screen
            // Try to find a *similar* one. For example,
            // the parent may be "Landscape" and this device
            // may have "Landscape,Closed" and "Landscape,Open"
            // as is the case with device "3.2in HGVA slider (ADP1)".
            int nameLen = name.length();
            for (State s : device.getAllStates()) {
              String n = s.getName();
              if (n.regionMatches(0, name, 0, Math.min(nameLen, n.length()))) {
                return s;
              }
            }

            return device.getDefaultState();
          }
        }
      }

      return state;
    }
  }

  @Override
  public void setDeviceState(State state) {
    if (isOverridingDeviceState()) {
      super.setDeviceState(state);
    }
    else {
      if (isOverridingDevice()) {
        Device device = super.getDevice();
        if (device != null) {
          State equivalentState = device.getState(state.getName());
          if (equivalentState != null) {
            state = equivalentState;
          }
        }
      }
      myParent.setDeviceState(state);
    }
  }

  /**
   * Sets whether the night mode should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideNightMode(boolean override) {
    if (override) {
      myOverride |= CFG_NIGHT_MODE;
    } else {
      myOverride &= ~CFG_NIGHT_MODE;
    }
  }

  /**
   * Returns true if the night mode is overridden
   *
   * @return true if the night mode is overridden
   */
  public final boolean isOverridingNightMode() {
    return (myOverride & CFG_NIGHT_MODE) != 0;
  }

  @Override
  @NotNull
  public NightMode getNightMode() {
    if (isOverridingNightMode()) {
      return super.getNightMode();
    }
    else {
      return myParent.getNightMode();
    }
  }

  @Override
  public void setNightMode(@NotNull NightMode night) {
    if (isOverridingNightMode()) {
      super.setNightMode(night);
    }
    else {
      myParent.setNightMode(night);
    }
  }

  /**
   * Sets whether the UI mode should be overridden by this configuration
   *
   * @param override if true, override the inherited value
   */
  public void setOverrideUiMode(boolean override) {
    if (override) {
      myOverride |= CFG_UI_MODE;
    } else {
      myOverride &= ~CFG_UI_MODE;
    }
  }

  /**
   * Returns true if the UI mode is overridden
   *
   * @return true if the UI mode is overridden
   */
  public final boolean isOverridingUiMode() {
    return (myOverride & CFG_UI_MODE) != 0;
  }

  @Override
  @NotNull
  public UiMode getUiMode() {
    if (isOverridingUiMode()) {
      return super.getUiMode();
    }
    else {
      return myParent.getUiMode();
    }
  }

  @Override
  public void setUiMode(@NotNull UiMode uiMode) {
    if (isOverridingUiMode()) {
      super.setUiMode(uiMode);
    }
    else {
      myParent.setUiMode(uiMode);
    }
  }

  /**
   * Returns the configuration this {@linkplain NestedConfiguration} is
   * inheriting from
   *
   * @return the configuration this configuration is inheriting from
   */
  @NotNull
  public Configuration getParent() {
    return myParent;
  }

  @Override
  @Nullable
  public String getActivity() {
    return myParent.getActivity();
  }

  @Override
  public void setActivity(String activity) {
    super.setActivity(activity);
  }

  /**
   * Returns a computed display name (ignoring the value stored by
   * {@link #setDisplayName(String)}) by looking at the override flags
   * and picking a suitable name.
   *
   * @return a suitable display name
   */
  @Nullable
  public String computeDisplayName() {
    return computeDisplayName(myOverride, this);
  }

  /**
   * Computes a display name for the given configuration, using the given
   * override flags (which correspond to the {@code CFG_} constants in
   * {@link ConfigurationListener}
   *
   * @param flags         the override bitmask
   * @param configuration the configuration to fetch values from
   * @return a suitable display name
   */
  @Nullable
  public static String computeDisplayName(int flags, @NotNull Configuration configuration) {
    if ((flags & CFG_LOCALE) != 0) {
      return LocaleMenuAction.getLocaleLabel(configuration.getLocale(), false);
    }

    if ((flags & CFG_TARGET) != 0) {
      return TargetMenuAction.getRenderingTargetLabel(configuration.getTarget(), false);
    }

    if ((flags & CFG_DEVICE) != 0) {
      return DeviceMenuAction.getDeviceLabel(configuration.getDevice(), true);
    }

    if ((flags & CFG_DEVICE_STATE) != 0) {
      State deviceState = configuration.getDeviceState();
      if (deviceState != null) {
        return deviceState.getName();
      }
    }

    if ((flags & CFG_NIGHT_MODE) != 0) {
      return configuration.getNightMode().getLongDisplayValue();
    }

    if ((flags & CFG_UI_MODE) != 0) {
      configuration.getUiMode().getLongDisplayValue();
    }

    return null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this.getClass()).add("parent", myParent.getDisplayName())
      .add("display", getDisplayName())
      .add("overrideLocale", isOverridingLocale())
      .add("overrideTarget", isOverridingTarget())
      .add("overrideDevice", isOverridingDevice())
      .add("overrideDeviceState", isOverridingDeviceState())
      .add("inherited", super.toString())
      .toString();
  }


  @Override
  public void dispose() {
    myParent.removeListener(this);
  }

  @Override
  public boolean changed(int flags) {
    // Mask out the flags that we are overriding; those changes do not affect us
    flags &= ~myOverride;
    if (flags != 0) {
      updated(flags);
    }
    return true;
  }
}
