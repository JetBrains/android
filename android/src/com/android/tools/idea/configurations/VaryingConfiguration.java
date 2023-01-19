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

import static com.android.ide.common.rendering.HardwareConfigHelper.isTv;
import static com.android.ide.common.rendering.HardwareConfigHelper.isWear;

import com.android.ide.common.resources.Locale;
import com.android.resources.Density;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@linkplain VaryingConfiguration} is a {@link Configuration} which
 * inherits all of its values from a different configuration, except for one or
 * more attributes where it overrides a custom value, and the overridden value
 * will always <b>differ</b> from the inherited value!
 * <p/>
 * For example, a {@linkplain VaryingConfiguration} may state that it
 * overrides the locale, and if the inherited locale is "en", then the returned
 * locale from the {@linkplain VaryingConfiguration} may be for example "nb",
 * but never "en".
 * <p/>
 * The configuration will attempt to make its changed inherited value to be as
 * different as possible from the inherited value. Thus, a configuration which
 * overrides the device will probably return a phone-sized screen if the
 * inherited device is a tablet, or vice versa.
 */
public class VaryingConfiguration extends NestedConfiguration {
  /**
   * Variation version; see {@link #setVariation(int)}
   */
  private int myVariation;

  /**
   * Variation version count; see {@link #setVariationCount(int)}
   */
  private int myVariationCount;

  /**
   * Bitmask of attributes to be varied/alternated from the parent
   */
  private int myAlternate;

  /**
   * Constructs a new {@linkplain VaryingConfiguration}.
   * Construct via {@link #create(Configuration)} or
   * {@link #create(VaryingConfiguration, Configuration)}
   *
   * @param configuration the configuration to inherit from
   */
  private VaryingConfiguration(@NotNull Configuration configuration) {
    super(configuration);
  }

  /**
   * Creates a new {@linkplain Configuration} which inherits values from the
   * given parent {@linkplain Configuration}, possibly overriding some as
   * well.
   *
   * @param parent the configuration to inherit values from
   * @return a new configuration
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static VaryingConfiguration create(@NotNull Configuration parent) {
    return new VaryingConfiguration(parent);
  }

  /**
   * Creates a new {@linkplain VaryingConfiguration} that has the same overriding
   * attributes as the given other {@linkplain VaryingConfiguration}.
   *
   * @param other  the configuration to copy overrides from
   * @param parent the parent to tie the configuration to for inheriting values
   * @return a new configuration
   */
  @NotNull
  public static VaryingConfiguration create(@NotNull VaryingConfiguration other, @NotNull Configuration parent) {
    VaryingConfiguration configuration = new VaryingConfiguration(parent);
    configuration.startBulkEditing();
    initFrom(configuration, other, other);
    configuration.myAlternate = other.myAlternate;
    configuration.myVariation = other.myVariation;
    configuration.myVariationCount = other.myVariationCount;
    configuration.finishBulkEditing();

    return configuration;
  }

  /**
   * Returns the alternate flags for this configuration. Corresponds to
   * the {@code CFG_} flags in {@link ConfigurationListener}.
   *
   * @return the bitmask
   */
  public int getAlternateFlags() {
    return myAlternate;
  }

  @Override
  protected void syncFolderConfig() {
    super.syncFolderConfig();
    updateDisplayName();
  }

  /**
   * Sets the variation version for this
   * {@linkplain VaryingConfiguration}. There might be multiple
   * {@linkplain VaryingConfiguration} instances inheriting from a
   * {@link Configuration}. The variation version allows them to choose
   * different complementing values, so they don't all flip to the same other
   * (out of multiple choices) value. The {@link #setVariationCount(int)}
   * value can be used to determine how to partition the buckets of values.
   * Also updates the variation count if necessary.
   *
   * @param variation variation version
   */
  public void setVariation(int variation) {
    myVariation = variation;
    myVariationCount = Math.max(myVariationCount, variation + 1);
  }

  /**
   * Sets the number of {@link VaryingConfiguration} variations mapped
   * to the same parent configuration as this one. See
   * {@link #setVariation(int)} for details.
   *
   * @param count the total number of variation versions
   */
  public void setVariationCount(int count) {
    myVariationCount = count;
  }

  /**
   * Updates the display name in this configuration based on the values and override settings
   */
  public void updateDisplayName() {
    setDisplayName(computeDisplayName());
  }

  @Override
  @NotNull
  public Locale getLocale() {
    if (isOverridingLocale()) {
      return super.getLocale();
    }
    Locale locale = myParent.getLocale();
    if (isAlternatingLocale()) {
      ImmutableList<Locale> locales = getConfigurationManager().getLocalesInProject();
      for (Locale l : locales) {
        // TODO: Try to be smarter about which one we pick; for example, try
        // to pick a language that is substantially different from the inherited
        // language, such as either with the strings of the largest or shortest
        // length, or perhaps based on some geography or population metrics
        if (!l.equals(locale)) {
          locale = l;
          break;
        }
      }
    }

    return locale;
  }

  @Override
  @Nullable
  public IAndroidTarget getTarget() {
    if (isOverridingTarget()) {
      return super.getTarget();
    }
    IAndroidTarget target = myParent.getTarget();
    if (isAlternatingTarget() && target != null) {
      ConfigurationManager manager = getConfigurationManager();
      IAndroidTarget[] targets = manager.getTargets();
      if (targets.length > 0) {
        // Pick a different target: if you're showing the most recent render target,
        // then pick the lowest supported target, and vice versa
        IAndroidTarget mostRecent = manager.getHighestApiTarget();
        if (target.equals(mostRecent)) {
          // Find oldest supported
          AndroidModuleInfo info = StudioAndroidModuleInfo.getInstance(manager.getModule());
          if (info != null) {
            int minSdkVersion = info.getMinSdkVersion().getFeatureLevel();
            for (IAndroidTarget t : targets) {
              if (t.getVersion().getFeatureLevel() >= minSdkVersion && ConfigurationManager.isLayoutLibTarget(t)) {
                target = t;
                break;
              }
            }
          }
        }
        else {
          target = mostRecent;
        }
      }
    }

    return target;
  }

  // Cached values, key=parent's device, cached value=device
  private Device myPrevParentDevice;
  private Device myPrevDevice;

  @Override
  @Nullable
  public Device getDevice() {
    if (isOverridingDevice()) {
      return super.getDevice();
    }
    Device device = myParent.getDevice();
    if (isAlternatingDevice() && device != null) {
      if (device == myPrevParentDevice) {
        return myPrevDevice;
      }

      myPrevParentDevice = device;

      // Pick a different device
      List<Device> devices = getConfigurationManager().getDevices();

      // Divide up the available devices into {@link #myVariationCount} + 1 buckets
      // (the + 1 is for the bucket now taken up by the inherited value).
      // Then assign buckets to each {@link #myVariation} version, and pick one
      // from the bucket assigned to this current configuration's variation version.

      // I could just divide up the device list count, but that would treat a lot of
      // very similar phones as having the same kind of variety as the 7" and 10"
      // tablets which are sitting right next to each other in the device list.
      // Instead, do this by screen size.

      boolean isTv = isTv(device);
      boolean isWear = isWear(device);

      double smallest = 100;
      double biggest = 1;
      for (Device d : devices) {
        double size = getScreenSize(d);
        if (size < 0) {
          continue; // no data
        } else if (isTv != isTv(d) || isWear != isWear(d)) {
          continue;
        }
        if (size >= biggest) {
          biggest = size;
        }
        if (size <= smallest) {
          smallest = size;
        }
      }

      int bucketCount = myVariationCount + 1;
      double inchesPerBucket = (biggest - smallest) / bucketCount;

      double overriddenSize = getScreenSize(device);
      int overriddenBucket = (int)((overriddenSize - smallest) / inchesPerBucket);
      int bucket = (myVariation < overriddenBucket) ? myVariation : myVariation + 1;
      double from = inchesPerBucket * bucket + smallest;
      double to = from + inchesPerBucket;
      if (biggest - to < 0.1) {
        to = biggest + 0.1;
      }

      for (Device d : devices) {
        if (isTv != isTv(d) || isWear != isWear(d)) {
          continue;
        }
        double size = getScreenSize(d);
        if (size >= from && size < to) {
          device = d;
          break;
        }
      }

      myPrevDevice = device;
    }

    return device;
  }

  /**
   * Returns the density of the given device
   *
   * @param device the device to check
   * @return the density or null
   */
  @Nullable
  private static Density getDensity(@NotNull Device device) {
    Hardware hardware = device.getDefaultHardware();
    Screen screen = hardware.getScreen();
    if (screen != null) {
      return screen.getPixelDensity();
    }

    return null;
  }

  /**
   * Returns the diagonal length of the given device
   *
   * @param device the device to check
   * @return the diagonal length or -1
   */
  private static double getScreenSize(@NotNull Device device) {
    Hardware hardware = device.getDefaultHardware();
    Screen screen = hardware.getScreen();
    if (screen != null) {
      return screen.getDiagonalLength();
    }

    return -1;
  }

  @Override
  @Nullable
  public State getDeviceState() {
    if (isOverridingDeviceState()) {
      return super.getDeviceState();
    }
    State state = myParent.getDeviceState();
    if (isAlternatingDeviceState() && state != null) {
      State next = getNextDeviceState(state);
      if (next != null) {
        return next;
      } else {
        return state;
      }
    }
    else {
      if ((isAlternatingDevice() || isOverridingDevice()) && state != null) {
        // If the device differs, I need to look up a suitable equivalent state
        // on our device
        Device device = getDevice();
        if (device != null) {
          return device.getState(state.getName());
        }
      }

      return state;
    }
  }

  @Override
  @NotNull
  public NightMode getNightMode() {
    if (isOverridingNightMode()) {
      return super.getNightMode();
    }
    NightMode nightMode = myParent.getNightMode();
    if (isAlternatingNightMode()) {
      nightMode = nightMode == NightMode.NIGHT ? NightMode.NOTNIGHT : NightMode.NIGHT;
    }
    return nightMode;
  }

  @Override
  @NotNull
  public UiMode getUiMode() {
    if (isOverridingUiMode()) {
      return super.getUiMode();
    }
    UiMode uiMode = myParent.getUiMode();
    if (isAlternatingUiMode()) {
      // TODO: Use manifest's supports screen to decide which are most relevant
      // (as well as which available configuration qualifiers are present in the
      // layout)
      UiMode[] values = UiMode.values();
      uiMode = values[(uiMode.ordinal() + 1) % values.length];
    }
    return uiMode;
  }

  @Override
  @Nullable
  public String computeDisplayName() {
    return computeDisplayName(getOverrideFlags() | myAlternate, this);
  }

  /**
   * Sets whether the locale should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateLocale(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_LOCALE;
    } else {
      myAlternate &= ~CFG_LOCALE;
    }
  }

  /**
   * Returns true if the locale is alternated
   *
   * @return true if the locale is alternated
   */
  public final boolean isAlternatingLocale() {
    return (myAlternate & CFG_LOCALE) != 0;
  }

  /**
   * Sets whether the rendering target should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateTarget(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_TARGET;
    } else {
      myAlternate &= ~CFG_TARGET;
    }
  }

  /**
   * Returns true if the target is alternated
   *
   * @return true if the target is alternated
   */
  public final boolean isAlternatingTarget() {
    return (myAlternate & CFG_TARGET) != 0;
  }

  /**
   * Sets whether the device should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateDevice(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_DEVICE;
    } else {
      myAlternate &= ~CFG_DEVICE;
    }
  }

  /**
   * Returns true if the device is alternated
   *
   * @return true if the device is alternated
   */
  public final boolean isAlternatingDevice() {
    return (myAlternate & CFG_DEVICE) != 0;
  }

  /**
   * Sets whether the device state should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateDeviceState(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_DEVICE_STATE;
    } else {
      myAlternate &= ~CFG_DEVICE_STATE;
    }
  }

  /**
   * Returns true if the device state is alternated
   *
   * @return true if the device state is alternated
   */
  public final boolean isAlternatingDeviceState() {
    return (myAlternate & CFG_DEVICE_STATE) != 0;
  }

  /**
   * Sets whether the night mode should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateNightMode(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_NIGHT_MODE;
    } else {
      myAlternate &= ~CFG_NIGHT_MODE;
    }
  }

  /**
   * Returns true if the night mode is alternated
   *
   * @return true if the night mode is alternated
   */
  public final boolean isAlternatingNightMode() {
    return (myAlternate & CFG_NIGHT_MODE) != 0;
  }

  /**
   * Sets whether the UI mode should be alternated by this configuration
   *
   * @param alternate if true, alternate the inherited value
   */
  public void setAlternateUiMode(boolean alternate) {
    if (alternate) {
      myAlternate |= CFG_UI_MODE;
    } else {
      myAlternate &= ~CFG_UI_MODE;
    }
  }

  /**
   * Returns true if the UI mode is alternated
   *
   * @return true if the UI mode is alternated
   */
  public final boolean isAlternatingUiMode() {
    return (myAlternate & CFG_UI_MODE) != 0;
  }

  @Override
  public boolean changed(int flags) {
    updated(flags);
    return true;
  }
}
