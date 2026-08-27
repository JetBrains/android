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
package com.android.tools.configurations;

import static com.android.tools.configurations.ConfigurationListener.MASK_FOLDERCONFIG;
import static java.util.Locale.ROOT;
import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.NightMode;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.res.FrameworkOverlay;
import com.google.common.base.Enums;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@linkplain Configuration} is a selection of device, orientation, theme,
 * etc for use when rendering a layout.
 */
public class Configuration {
  public static final String CUSTOM_DEVICE_ID = "Custom";

  // Aliases for external callers to preserve public API compatibility
  public static final int UI_MODE_TYPE_MASK = UiModeState.UI_MODE_TYPE_MASK;
  public static final int UI_MODE_NIGHT_YES = UiModeState.UI_MODE_NIGHT_YES;
  public static final int UI_MODE_NIGHT_NO = UiModeState.UI_MODE_NIGHT_NO;

  private static final ResourceReference postSplashAttrReference = ResourceReference.attr(
    ResourceNamespace.RES_AUTO, "postSplashScreenTheme"
  );
  /**
   * The {@link FolderConfiguration} representing the state of the UI controls
   */
  @NotNull
  protected final FolderConfiguration myFullConfig = new FolderConfiguration();

  /** The associated {@link ConfigurationSettings} */
  @NotNull
  protected final ConfigurationSettings mySettings;

  /**
   * The {@link FolderConfiguration} being edited.
   */
  @NotNull
  protected final FolderConfiguration myEditedConfig;

  /** Handles listener notifications and bulk editing count */
  private final ConfigurationListeners myListeners = new ConfigurationListeners();

  /** Dirty flags since last notify: corresponds to constants in {@link ConfigurationListener} */
  protected int myNotifyDirty;

  /** Dirty flags since last folder config sync: corresponds to constants in {@link ConfigurationListener} */
  protected int myFolderConfigDirty = MASK_FOLDERCONFIG;

  protected int myProjectStateVersion;

  private long myModificationCount;

  private final SystemUiPreferences mySystemUiPrefs = new SystemUiPreferences();

  private final UiModeState myUiModeState = new UiModeState();

  private final DeviceStateResolver myDeviceStateResolver;

  private final EnvironmentContext myEnvContext;

  private final ResourceItemResolver.ResourceProvider myResourceProvider = new ConfigurationResourceProvider(this);

  /**
   * Creates a new {@linkplain Configuration}
   */
  protected Configuration(@NotNull ConfigurationSettings settings, @NotNull FolderConfiguration editedConfig) {
    mySettings = settings;
    myEditedConfig = editedConfig;

    myDeviceStateResolver = new DeviceStateResolver(new DeviceStateResolver.Context() {
      @NotNull @Override public ConfigurationSettings getSettings() { return mySettings; }
      @NotNull @Override public FolderConfiguration getEditedConfig() { return myEditedConfig; }
      @Override public void updateDeviceOverlay() { Configuration.this.updateDeviceOverlay(); }
      @Nullable @Override public Device computeBestDevice() { return Configuration.this.computeBestDevice(); }
    });

    myEnvContext = new EnvironmentContext(new EnvironmentContext.Context() {
      @NotNull @Override public ConfigurationSettings getSettings() { return mySettings; }
      @NotNull @Override public FolderConfiguration getEditedConfig() { return myEditedConfig; }
      @Nullable @Override public String calculateActivity() { return Configuration.this.calculateActivity(); }
      @NotNull @Override public String getPreferredTheme() { return Configuration.this.getPreferredTheme(); }
      @Nullable @Override public IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target) {
        return Configuration.getTargetForRendering(target, mySettings.getConfigModule());
      }
    });

    if (isLocaleSpecificLayout()) {
      myEnvContext.initFromEditedConfig();
    }

    if (isOrientationSpecificLayout()) {
      myDeviceStateResolver.initFromEditedConfig();
    }
  }

  /**
   * Multiple image transformation types can be applied at once to the configuration but only of each type.
   */
  public enum ImageTransformationType {
    /** Reserved for transformations for Color Blind mode in the surface. */
    COLOR_BLIND_MODE,
    /** Reserved to apply backgrounds for AI Glasses. */
    GLASSES_BACKGROUND_IMAGE
  };

  /**
   * Creates a new {@linkplain Configuration}
   *
   * @return a new configuration
   */
  @NotNull
  public static Configuration create(@NotNull ConfigurationSettings settings, @NotNull FolderConfiguration editedConfig) {
    return new Configuration(settings, editedConfig);
  }

  protected void copyFrom(@NotNull Configuration from) {
    myFullConfig.set(from.myFullConfig);
    myFolderConfigDirty = from.myFolderConfigDirty;
    myProjectStateVersion = from.myProjectStateVersion;

    myEnvContext.copyFrom(from.myEnvContext);
    myUiModeState.copyFrom(from.myUiModeState);
    myDeviceStateResolver.copyFrom(from.myDeviceStateResolver);
    mySystemUiPrefs.copyFrom(from.mySystemUiPrefs);
  }

  @Override
  public Configuration clone() {
    Configuration copy = new Configuration(this.mySettings, FolderConfiguration.copyOf(this.getEditedConfig()));
    copy.copyFrom(this);
    return copy;
  }

  @Nullable
  protected String getStateName() {
    return myDeviceStateResolver.getStateName();
  }

  public void save() { }

  /**
   * Returns the associated {@link ConfigurationSettings}
   *
   * @return the settings
   */
  @NotNull
  public ConfigurationSettings getSettings() {
    return mySettings;
  }

  @Nullable
  protected String calculateActivity() {
    return null;
  }

  /**
   * Returns the associated activity
   *
   * @return the activity
   */
  @Nullable
  public final String getActivity() {
    return myEnvContext.getActivity();
  }

  /**
   * Returns the chosen device, computing the best one if the currently cached value is null.
   *
   * Use {@link #getCachedDevice()} to get the current cached device regardless of its nullability.
   */
  @Slow
  @Nullable
  public Device getDevice() {
    return myDeviceStateResolver.getDevice();
  }

  /**
   * Returns the current value of the effective device. Please note this will return the cached value of the field, which is actually
   * computed in {@link #getDevice()}.
   */
  @Nullable
  public Device getCachedDevice() {
    return myDeviceStateResolver.getCachedDevice();
  }

  @Nullable
  public static FolderConfiguration getFolderConfig(@NotNull ConfigurationModelModule module, @NotNull State state, @NotNull Locale locale,
                                                    @Nullable IAndroidTarget target) {
    return FolderConfigSynchronizer.getFolderConfig(module, state, locale, target);
  }

  @Slow
  @Nullable
  protected Device computeBestDevice() {
    return mySettings.getDefaultDevice();
  }

  /**
   * Returns the chosen device state
   *
   * @return the device state
   */
  @Nullable
  public State getDeviceState() {
    return myDeviceStateResolver.getDeviceState();
  }

  /**
   * Returns the current value of the effective device state. Please note this will return the cached value of the field, which is actually
   * computed in {@link #getDeviceState()}.
   */
  @Nullable
  public State getCachedDeviceState() {
    return myDeviceStateResolver.getCachedState();
  }

  /**
   * Returns the chosen locale
   *
   * @return the locale
   */
  @NotNull
  public Locale getLocale() {
    return myEnvContext.getLocale();
  }

  /**
   * Returns the UI mode
   *
   * @return the UI mode
   */
  @NotNull
  public UiMode getUiMode() {
    return myUiModeState.getUiMode();
  }

  /**
   * Returns the day/night mode
   *
   * @return the night mode
   */
  @NotNull
  public NightMode getNightMode() {
    return myUiModeState.getNightMode();
  }

  /**
   * Returns the current theme style name, in the form @style/ThemeName or @android:style/ThemeName
   *
   * @return the theme style name
   */
  @NotNull
  public String getTheme() {
    return myEnvContext.getTheme();
  }

  /**
   * Returns the rendering target
   *
   * @return the target
   */
  @Nullable
  public IAndroidTarget getTarget() {
    return myEnvContext.getTarget();
  }

  /**
   * Returns the configuration target. This will be different of {#getTarget} when using newer targets to render on screen.
   * This method can be used to obtain a target that can be used for attribute resolution.
   */
  @Nullable
  public IAndroidTarget getRealTarget() {
    return myEnvContext.getRealTarget();
  }

  /**
   * Returns the display name to show for this configuration
   *
   * @return the display name, or null if none has been assigned
   */
  @Nullable
  public String getDisplayName() {
    return myEnvContext.getDisplayName();
  }

  /**
   * Returns true if the current layout is locale-specific
   *
   * @return if this configuration represents a locale-specific layout
   */
  public boolean isLocaleSpecificLayout() {
    return myEditedConfig.getLocaleQualifier() != null;
  }

  /**
   * Returns true if the current layout is target-specific
   *
   * @return if this configuration represents a target-specific layout
   */
  public boolean isTargetSpecificLayout() {
    return myEditedConfig.getVersionQualifier() != null;
  }

  /**
   * Returns true if the current layout is orientation-specific
   *
   * @return if this configuration represents a orientation-specific layout
   */
  public boolean isOrientationSpecificLayout() {
    return myEditedConfig.getScreenOrientationQualifier() != null;
  }

  /**
   * Returns the full, complete {@link FolderConfiguration}
   *
   * @return the full configuration
   */
  @NotNull
  public FolderConfiguration getFullConfig() {
    if ((myFolderConfigDirty & MASK_FOLDERCONFIG) != 0 || myProjectStateVersion != mySettings.getStateVersion()) {
      syncFolderConfig();
    }

    return myFullConfig;
  }

  /**
   * Returns the edited {@link FolderConfiguration} (this is not a full configuration, so you can think of it as the "constraints" used by
   * the {@code ConfigurationMatcher} to produce a full configuration.
   *
   * @return the constraints configuration
   */
  @NotNull
  public FolderConfiguration getEditedConfig() {
    return myEditedConfig;
  }

  /**
   * Sets the associated activity
   *
   * @param activity the activity
   */
  public void setActivity(@Nullable String activity) {
    int updateFlags = myEnvContext.setActivity(activity);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the device
   *
   * @param device        the device
   * @param preserveState if true, attempt to preserve the state associated with the config
   */
  public void setDevice(Device device, boolean preserveState) {
    int updateFlags = myDeviceStateResolver.setDevice(device, preserveState);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the device state
   *
   * @param state the device state
   */
  public void setDeviceState(State state) {
    int updateFlags = myDeviceStateResolver.setDeviceState(state);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the device state name
   *
   * @param stateName the device state name
   */
  public void setDeviceStateName(@Nullable String stateName) {
    int updateFlags = myDeviceStateResolver.setDeviceStateName(stateName);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the locale
   *
   * @param locale the locale
   */
  public void setLocale(@NotNull Locale locale) {
    int updateFlags = myEnvContext.setLocale(locale);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the rendering target
   *
   * @param target rendering target
   */
  public void setTarget(@Nullable IAndroidTarget target) {
    int updateFlags = myEnvContext.setTarget(target);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the display name to be shown for this configuration.
   *
   * @param displayName the new display name
   */
  public void setDisplayName(@Nullable String displayName) {
    int updateFlags = myEnvContext.setDisplayName(displayName);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the night mode
   *
   * @param night the night mode
   */
  public void setNightMode(@NotNull NightMode night) {
    int updateFlags = myUiModeState.setNightMode(night);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the UI mode
   *
   * @param uiMode the UI mode
   */
  public void setUiMode(@NotNull UiMode uiMode) {
    int updateFlags = myUiModeState.setUiMode(uiMode);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets the raw value for uiMode. When setting it using this method, both UiMode and night mode might be updated as result.
   */
  public void setUiModeFlagValue(int uiMode) {
    int updateFlags = myUiModeState.setUiModeFlagValue(uiMode);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Returns the current flags for uiMode.
   */
  public int getUiModeFlagValue() {
    return myUiModeState.getUiModeFlagValue();
  }

  /**
   * Sets the theme style
   *
   * @param theme the theme
   */
  public void setTheme(@Nullable String theme) {
    int updateFlags = myEnvContext.setTheme(theme);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  /**
   * Sets user preference for the scaling factor for fonts, relative to the base density scaling.
   * See {@code android.content.res.Configuration#fontScale}
   *
   * @param fontScale The new scale. Must be greater than 0
   */
  public void setFontScale(float fontScale) {
    assert fontScale > 0f : "fontScale must be greater than 0";

    if (mySystemUiPrefs.getFontScale() != fontScale) {
      mySystemUiPrefs.setFontScale(fontScale);
      updated(ConfigurationListener.CFG_FONT_SCALE);
    }
  }

  /**
   * Returns user preference for the scaling factor for fonts, relative to the base density scaling.
   * See {@code android.content.res.Configuration#fontScale}
   */
  public float getFontScale() {
    return mySystemUiPrefs.getFontScale();
  }

  /**
   * Sets the {@link AdaptiveIconShape} to use when rendering
   */
  public void setAdaptiveShape(@NotNull AdaptiveIconShape adaptiveShape) {
    if (mySystemUiPrefs.getAdaptiveShape() != adaptiveShape) {
      mySystemUiPrefs.setAdaptiveShape(adaptiveShape);
      updated(ConfigurationListener.CFG_ADAPTIVE_SHAPE);
    }
  }

  /**
   * Returns the {@link AdaptiveIconShape} to use when rendering
   */
  @NotNull
  public AdaptiveIconShape getAdaptiveShape() {
    return mySystemUiPrefs.getAdaptiveShape();
  }

  public void setWallpaper(@Nullable Wallpaper wallpaper) {
    if (!Objects.equals(mySystemUiPrefs.getWallpaper(), wallpaper)) {
      mySystemUiPrefs.setWallpaper(wallpaper);
      mySystemUiPrefs.setUseThemedIcon(wallpaper != null);
      updated(ConfigurationListener.CFG_THEME);
    }
  }

  /**
   * Returns the wallpaper resource path to use when rendering
   */
  @Nullable
  public String getWallpaperPath() {
    return mySystemUiPrefs.getWallpaper() != null ? mySystemUiPrefs.getWallpaper().getResourcePath() : null;
  }

  /**
   * Sets whether the rendering should be edge-to-edge
   */
  public void setEdgeToEdge(boolean edgeToEdge) {
    mySystemUiPrefs.setEdgeToEdge(edgeToEdge);
  }

  /**
   * Returns whether the rendering should be edge-to-ege
   */
  public boolean isEdgeToEdge() {
    return mySystemUiPrefs.isEdgeToEdge();
  }

  /**
   * Sets whether the rendering should use the gesture version of the navigation bar
   */
  public void setGestureNav(boolean gestureNav) {
    mySystemUiPrefs.setGestureNav(gestureNav);
  }

  /**
   * Returns whether the rendering should use the gesture version of the navigation bar
   */
  public boolean isGestureNav() {
    return mySystemUiPrefs.isGestureNav();
  }

  /**
   * Sets the overlay to use for displaying the display cutout
   */
  public void setCutoutOverlay(FrameworkOverlay overlay) {
    mySystemUiPrefs.setCutoutOverlay(overlay);
  }

  public FrameworkOverlay getCutoutOverlay() {
    return mySystemUiPrefs.getCutoutOverlay();
  }

  /**
   * Sets the consumer that applies a transformation function to the rendered image.
   *
   * @param type the type of the transformation function to retrieve.
   * @param imageTransformation the consumer containing a transformation function to be applied to the rendered image
   */
  public void setImageTransformation(@NotNull ImageTransformationType type,
                                     @Nullable Consumer<BufferedImage> imageTransformation) {
    mySystemUiPrefs.setImageTransformation(type, imageTransformation);
  }

  /**
   * Returns a {@link Consumer} that applies all transformations that have been set via
   * {@link #setImageTransformation}.
   *
   * @return the image transformation consumer
   */
  @Nullable
  public Consumer<BufferedImage> getImageTransformation() {
    return mySystemUiPrefs.getImageTransformation();
  }


  /**
   * Returns whether to use the themed version of adaptive icons
   */
  public boolean getUseThemedIcon() {
    return mySystemUiPrefs.getUseThemedIcon();
  }

  /**
   * Updates the folder configuration such that it reflects changes in
   * configuration state such as the device orientation, the UI mode, the
   * rendering target, etc.
   */
  protected void syncFolderConfig() {
    FolderConfigSynchronizer.sync(
      myFullConfig,
      mySettings,
      myEditedConfig,
      getDevice(),
      getDeviceState(),
      getLocale(),
      getTarget(),
      getUiMode(),
      getNightMode()
    );

    myFolderConfigDirty = 0;
    myProjectStateVersion = mySettings.getStateVersion();
  }

  /** Returns the screen size required for this configuration */
  @Nullable
  public ScreenSize getScreenSize() {
    return myDeviceStateResolver.getScreenSize();
  }

  /**
   * Returns the currently selected {@link Density}. This is guaranteed to be non null.
   *
   * @return the density
   */
  @NotNull
  public Density getDensity() {
    DensityQualifier qualifier = getFullConfig().getDensityQualifier();
    if (qualifier != null) {
      // just a sanity check
      Density d = qualifier.getValue();
      if (d != null && d.isValidValueForDevice()) {
        return d;
      }
    }

    // no config? return medium as the default density.
    return Density.MEDIUM;
  }

  /**
   * Get the next cyclical state after the given state
   *
   * @param from the state to start with
   * @return the following state following
   */
  @Nullable
  public State getNextDeviceState(@Nullable State from) {
    return myDeviceStateResolver.getNextDeviceState(from);
  }

  /**
   * Marks the beginning of a "bulk" editing operation with repeated calls to
   * various setters. After all the values have been set, the client <b>must</b>
   * call {@link #finishBulkEditing()}. This allows configurations to avoid
   * doing {@link FolderConfiguration} syncing for intermediate stages, and all
   * listener updates are deferred until the bulk operation is complete.
   */
  public void startBulkEditing() {
    myListeners.startBulkEditing();
  }

  /**
   * Marks the end of a "bulk" editing operation. At this point listeners will
   * be notified of the cumulative changes, etc. See {@link #startBulkEditing()}
   * for details.
   */
  public void finishBulkEditing() {
    if (myListeners.finishBulkEditing()) {
      updated(0);
    }
  }

  /** Called when one or more attributes of the configuration has changed */
  public void updated(int flags) {
    myNotifyDirty |= flags;
    myFolderConfigDirty |= flags;
    myModificationCount++;

    if (!myListeners.isBulkEditing()) {
      int changed = myNotifyDirty;
      myListeners.notifyListeners(changed);

      myNotifyDirty = 0;
    }
  }

  /**
   * Adds a listener to be notified when the configuration changes
   *
   * @param listener the listener to add
   */
  public void addListener(@NotNull ConfigurationListener listener) {
    myListeners.addListener(listener);
  }

  /**
   * Removes a listener such that it is no longer notified of changes
   *
   * @param listener the listener to remove
   */
  public void removeListener(@NotNull ConfigurationListener listener) {
    myListeners.removeListener(listener);
  }

  public void useDeviceForCutout(@NotNull String deviceId) {
    Optional<FrameworkOverlay> deviceOverlay = Enums.getIfPresent(FrameworkOverlay.class, deviceId.toUpperCase(ROOT));
    if (deviceOverlay.isPresent()) {
      mySystemUiPrefs.setDeviceOverlay(deviceOverlay.get());
    } else {
      mySystemUiPrefs.setDeviceOverlay(null);
    }
  }

  private void updateDeviceOverlay() {
    if (myDeviceStateResolver.getCachedDevice() == null) {
      mySystemUiPrefs.setDeviceOverlay(null);
    } else {
      useDeviceForCutout(myDeviceStateResolver.getCachedDevice().getId());
    }
  }

  // ---- Resolving resources ----

  @Slow
  public @NotNull ResourceResolver getResourceResolver() {
    String theme = getTheme();
    Device device = getDevice();
    List<FrameworkOverlay> overlays = getOverlays();
    ResourceResolverCache resolverCache = mySettings.getResolverCache();
    if (device != null && CUSTOM_DEVICE_ID.equals(device.getId())) {
      // Remove the old custom device configuration only if it's different from the new one
      resolverCache.replaceCustomConfig(theme, getFullConfig(), overlays);
    }
    return resolverCache.getResourceResolver(getTarget(), theme, getFullConfig(), overlays);
  }

  public @NotNull ResourceItemResolver getResourceItemResolver() {
    return new ResourceItemResolver(getFullConfig(), myResourceProvider, null);
  }

  @NotNull
  public List<FrameworkOverlay> getOverlays() {
    List<FrameworkOverlay> overlays = new ArrayList<>(3);
    overlays.add(mySystemUiPrefs.isGestureNav() ? FrameworkOverlay.NAV_GESTURE : FrameworkOverlay.NAV_3_BUTTONS);
    if (mySystemUiPrefs.getDeviceOverlay() != null) {
      overlays.add(mySystemUiPrefs.getDeviceOverlay());
    }
    overlays.add(mySystemUiPrefs.getCutoutOverlay());
    return overlays;
  }

  // For debugging only
  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this.getClass())
      .add("display", myEnvContext.getDisplayName())
      .add("theme", myEnvContext.getTheme())
      .add("activity", myEnvContext.getActivity())
      .add("device", myDeviceStateResolver.getCachedDevice())
      .add("state", myDeviceStateResolver.getCachedState())
      .add("locale", myEnvContext.getLocale())
      .add("target", myEnvContext.getTarget())
      .add("uimode", myUiModeState.getUiMode())
      .add("nightmode", myUiModeState.getNightMode())
      .add("fontScale", mySystemUiPrefs.getFontScale())
      .add("adaptiveShape", mySystemUiPrefs.getAdaptiveShape())
      .add("useThemedIcon", mySystemUiPrefs.getUseThemedIcon())
      .add("wallpaper", mySystemUiPrefs.getWallpaper())
      .add("deviceOverlay", mySystemUiPrefs.getDeviceOverlay())
      .add("gestureNav", mySystemUiPrefs.isGestureNav())
      .add("cutoutOverlay", mySystemUiPrefs.getCutoutOverlay())
      .add("edgeToEdge", mySystemUiPrefs.isEdgeToEdge())
      .toString();
  }

  @NotNull
  public ConfigurationModelModule getConfigModule() {
    return mySettings.getConfigModule();
  }

  public void setEffectiveDevice(@Nullable Device device, @Nullable State state) {
    int updateFlags = myDeviceStateResolver.setEffectiveDevice(device, state);
    if (updateFlags != 0) {
      updated(updateFlags);
    }
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  /**
   * Returns a target that is only suitable to be used for rendering (as opposed to a target that can be used for attribute resolution).
   */
  @Nullable
  private static IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target, @NotNull ConfigurationModelModule module) {
    if (target == null) {
      return null;
    }

    return module.getCompatibilityTarget(target);
  }

  /**
   * Returns a default theme name for this configuration.
   * This method takes into account the activity name and the device settings. It will also consider the manifest and the post splash
   * theme, if defined.
   */
  @NotNull
  public String getPreferredTheme() {
    return mySettings.getConfigModule().getThemeInfoProvider().getDefaultTheme(this);
  }
}