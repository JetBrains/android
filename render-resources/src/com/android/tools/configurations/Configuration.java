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

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.tools.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.configurations.ConfigurationListener.CFG_ADAPTIVE_SHAPE;
import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE;
import static com.android.tools.configurations.ConfigurationListener.CFG_DEVICE_STATE;
import static com.android.tools.configurations.ConfigurationListener.CFG_FONT_SCALE;
import static com.android.tools.configurations.ConfigurationListener.CFG_LOCALE;
import static com.android.tools.configurations.ConfigurationListener.CFG_NAME;
import static com.android.tools.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.configurations.ConfigurationListener.CFG_TARGET;
import static com.android.tools.configurations.ConfigurationListener.CFG_THEME;
import static com.android.tools.configurations.ConfigurationListener.CFG_UI_MODE;
import static com.android.tools.configurations.ConfigurationListener.MASK_FOLDERCONFIG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.LayoutDirection;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.layoutlib.LayoutlibContext;
import com.android.tools.res.FrameworkOverlay;
import com.android.tools.res.ResourceUtils;
import com.android.tools.sdk.AndroidPlatform;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.android.tools.sdk.LayoutlibFactory;
import com.google.common.base.MoreObjects;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@linkplain Configuration} is a selection of device, orientation, theme,
 * etc for use when rendering a layout.
 */
public class Configuration {
  public static final String CUSTOM_DEVICE_ID = "Custom";

  // Set of constants from {@link android.content.res.Configuration} to be used in setUiModeFlagValue.
  public static final int UI_MODE_TYPE_MASK = 0x0000000f;
  private static final int UI_MODE_TYPE_APPLIANCE = 0x00000005;
  private static final int UI_MODE_TYPE_CAR = 0x00000003;
  private static final int UI_MODE_TYPE_DESK = 0x00000002;
  private static final int UI_MODE_TYPE_NORMAL = 0x00000001;
  private static final int UI_MODE_TYPE_TELEVISION = 0x00000004;
  private static final int UI_MODE_TYPE_VR_HEADSET = 0x00000007;
  private static final int UI_MODE_TYPE_WATCH = 0x00000006;

  private static final int UI_MODE_NIGHT_MASK = 0x00000030;
  public static final int UI_MODE_NIGHT_YES = 0x00000020;
  public static final int UI_MODE_NIGHT_NO = 0x00000010;

  private static final ResourceReference postSplashAttrReference = ResourceReference.attr(
    ResourceNamespace.RES_AUTO, "postSplashScreenTheme"
  );
  /**
   * The {@link FolderConfiguration} representing the state of the UI controls
   */
  @NonNull
  protected final FolderConfiguration myFullConfig = new FolderConfiguration();

  /** The associated {@link ConfigurationSettings} */
  @NonNull
  protected final ConfigurationSettings mySettings;

  /**
   * The {@link FolderConfiguration} being edited.
   */
  @NonNull
  protected final FolderConfiguration myEditedConfig;

  /**
   * The target of the project of the file being edited.
   */
  @Nullable
  private IAndroidTarget myTarget;

  /**
   * The theme style to render with
   */
  @Nullable
  private String myTheme;

  /**
   * A specific device to render with
   */
  @Nullable
  private Device mySpecificDevice;

  /**
   * The specific device state
   */
  @Nullable
  private State myState;

  /**
   * The computed effective device; if this configuration does not have a hardcoded specific device,
   * it will be computed based on the current device list; this field caches the value.
   */
  @Nullable
  private Device myDevice;

  /**
   * The device state to use. Used to update {@link #getDeviceState()} such that it returns a state
   * suitable with whatever {@link #getDevice()} returns, since {@link #getDevice()} updates dynamically,
   * and the specific {@link State} instances are tied to actual devices (through the
   * {@link State#getHardware()} accessor).
   */
  @Nullable
  private String myStateName;

  /**
   * The activity associated with the layout. This is just a cached value of
   * the true value stored on the layout.
   */
  @Nullable
  private String myActivity;

  /**
   * The locale to use for this configuration
   */
  @Nullable
  private Locale myLocale = null;

  /**
   * UI mode
   */
  @NonNull
  private UiMode myUiMode = UiMode.NORMAL;

  /**
   * Night mode
   */
  @NonNull
  private NightMode myNightMode = NightMode.NOTNIGHT;

  /**
   * The display name
   */
  private String myDisplayName;

  /** For nesting count use by {@link #startBulkEditing()} and {@link #finishBulkEditing()} */
  private int myBulkEditingCount;

  /** Optional set of listeners to notify via {@link #updated(int)} */
  @Nullable
  private List<ConfigurationListener> myListeners;

  /** Dirty flags since last notify: corresponds to constants in {@link ConfigurationListener} */
  protected int myNotifyDirty;

  /** Dirty flags since last folder config sync: corresponds to constants in {@link ConfigurationListener} */
  protected int myFolderConfigDirty = MASK_FOLDERCONFIG;

  protected int myProjectStateVersion;

  private long myModificationCount;

  private float myFontScale = 1f;
  private int myUiModeFlagValue;
  @NonNull private AdaptiveIconShape myAdaptiveShape = AdaptiveIconShape.getDefaultShape();
  private boolean myUseThemedIcon = false;
  private Wallpaper myWallpaper = null;
  private Consumer<BufferedImage> myImageTransformation = null;
  private boolean myGestureNav = false;
  private boolean myEdgeToEdge = false;

  /**
   * Creates a new {@linkplain Configuration}
   */
  protected Configuration(@NonNull ConfigurationSettings settings, @NonNull FolderConfiguration editedConfig) {
    mySettings = settings;
    myEditedConfig = editedConfig;

    if (isLocaleSpecificLayout()) {
      myLocale = Locale.create(editedConfig);
    }

    if (isOrientationSpecificLayout()) {
      ScreenOrientationQualifier qualifier = editedConfig.getScreenOrientationQualifier();
      assert qualifier != null; // because isOrientationSpecificLayout()
      ScreenOrientation orientation = qualifier.getValue();
      if (orientation != null) {
        myStateName = orientation.getShortDisplayValue();
      }
    }
  }

  /**
   * Creates a new {@linkplain Configuration}
   *
   * @return a new configuration
   */
  @NonNull
  public static Configuration create(@NonNull ConfigurationSettings settings, @NonNull FolderConfiguration editedConfig) {
    return new Configuration(settings, editedConfig);
  }

  protected void copyFrom(@NonNull Configuration from) {
    myFullConfig.set(from.myFullConfig);
    myFolderConfigDirty = from.myFolderConfigDirty;
    myProjectStateVersion = from.myProjectStateVersion;
    myTarget = from.myTarget; // avoid getTarget() since it fetches project state
    myLocale = from.myLocale;  // avoid getLocale() since it fetches project state
    myTheme = from.getTheme();
    mySpecificDevice = from.mySpecificDevice;
    myDevice = from.myDevice; // avoid getDevice() since it fetches project state
    myStateName = from.myStateName;
    myState = from.myState;
    myActivity = from.getActivity();
    myUiMode = from.getUiMode();
    myNightMode = from.getNightMode();
    myDisplayName = from.getDisplayName();
    myFontScale = from.myFontScale;
    myUiModeFlagValue = from.myUiModeFlagValue;
    myAdaptiveShape = from.myAdaptiveShape;
    myUseThemedIcon = from.myUseThemedIcon;
    myWallpaper = from.myWallpaper;
  }

  @Override
  public Configuration clone() {
    Configuration copy = new Configuration(this.mySettings, FolderConfiguration.copyOf(this.getEditedConfig()));
    copy.copyFrom(this);
    return copy;
  }

  @Nullable
  protected String getStateName() {
    return myStateName;
  }

  public void save() { }

  /**
   * Returns the associated {@link ConfigurationSettings}
   *
   * @return the settings
   */
  @NonNull
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
    if (myActivity == NO_ACTIVITY) {
      return null;
    } else if (myActivity == null) {
      myActivity = calculateActivity();
      if (myActivity == null) {
        myActivity = NO_ACTIVITY;
        return null;
      }
    }

    return myActivity;
  }

  /** Special marker value which indicates that this activity has been checked and has no activity
   * (whereas a null {@link #myActivity} field means that it has not yet been initialized */
  private static final String NO_ACTIVITY = new String();

  /**
   * Returns the chosen device, computing the best one if the currently cached value is null.
   *
   * Use {@link #getCachedDevice()} to get the current cached device regardless of its nullability.
   */
  @Slow
  @Nullable
  public Device getDevice() {
    Device cached = getCachedDevice();
    if (cached != null) {
      return cached;
    }

    if (mySpecificDevice != null) {
      myDevice = mySpecificDevice;
    }
    else {
      myDevice = computeBestDevice();
    }
    return myDevice;
  }

  /**
   * Returns the current value of the effective device. Please note this will return the cached value of the field, which is actually
   * computed in {@link #getDevice()}.
   */
  @Nullable
  public Device getCachedDevice() {
    return myDevice;
  }

  @Nullable
  public static FolderConfiguration getFolderConfig(@NonNull ConfigurationModelModule module, @NonNull State state, @NonNull Locale locale,
                                                    @Nullable IAndroidTarget target) {
    FolderConfiguration currentConfig = DeviceConfigHelper.getFolderConfig(state);
    if (currentConfig != null) {
      if (locale.hasLanguage()) {
        currentConfig.setLocaleQualifier(locale.qualifier);
        LayoutLibrary layoutLib = getLayoutLibrary(target, module.getAndroidPlatform(), module.getLayoutlibContext());
        if (layoutLib != null) {
          if (layoutLib.isRtl(locale.toLocaleId())) {
            currentConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
          }
        }
      }
    }

    return currentConfig;
  }

  private static LayoutLibrary getLayoutLibrary(
    @Nullable IAndroidTarget target, @Nullable AndroidPlatform platform, @NonNull LayoutlibContext context) {
    if (target == null || platform == null) {
      return null;
    }

    try {
      return LayoutlibFactory.getLayoutLibrary(target, platform, context);
    } catch (RenderingException ignored) {
      return null;
    }
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
    if (myState == null) {
      Device device = getDevice();
      myState = DeviceState.getDeviceState(device, myStateName);
    }

    return myState;
  }

  /**
   * Returns the chosen locale
   *
   * @return the locale
   */
  @NonNull
  public Locale getLocale() {
    if (myLocale == null) {
      return mySettings.getLocale();
    }
    return myLocale;
  }

  /**
   * Returns the UI mode
   *
   * @return the UI mode
   */
  @NonNull
  public UiMode getUiMode() {
    return myUiMode;
  }

  /**
   * Returns the day/night mode
   *
   * @return the night mode
   */
  @NonNull
  public NightMode getNightMode() {
    return myNightMode;
  }

  /**
   * Returns the current theme style name, in the form @style/ThemeName or @android:style/ThemeName
   *
   * @return the theme style name
   */
  @NonNull
  public String getTheme() {
    if (myTheme == null) {
      return getPreferredTheme();
    }

    return myTheme;
  }

  /**
   * Returns the rendering target
   *
   * @return the target
   */
  @Nullable
  public IAndroidTarget getTarget() {
    if (myTarget == null) {
      IAndroidTarget target = mySettings.getTarget();

      // If the project-wide render target isn't a match for the version qualifier in this layout
      // (for example, the render target is at API 11, and layout is in a -v14 folder) then pick
      // a target which matches.
      VersionQualifier version = myEditedConfig.getVersionQualifier();
      if (target != null && version != null && version.getVersion() > target.getVersion().getFeatureLevel()) {
        target = mySettings.getTarget(version.getVersion());
      }

      return getTargetForRendering(target, mySettings.getConfigModule());
    }

    return myTarget;
  }

  /**
   * Returns the configuration target. This will be different of {#getTarget} when using newer targets to render on screen.
   * This method can be used to obtain a target that can be used for attribute resolution.
   */
  @Nullable
  public IAndroidTarget getRealTarget() {
    IAndroidTarget target = getTarget();

    if (target instanceof CompatibilityRenderTarget) {
      CompatibilityRenderTarget compatTarget = (CompatibilityRenderTarget)target;
      return compatTarget.getRealTarget();
    }
    else {
      return target;
    }
  }

  /**
   * Returns the display name to show for this configuration
   *
   * @return the display name, or null if none has been assigned
   */
  @Nullable
  public String getDisplayName() {
    return myDisplayName;
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
  @NonNull
  public FolderConfiguration getFullConfig() {
    if ((myFolderConfigDirty & MASK_FOLDERCONFIG) != 0 || myProjectStateVersion != mySettings.getStateVersion()) {
      syncFolderConfig();
    }

    return myFullConfig;
  }

  /**
   * Returns the edited {@link FolderConfiguration} (this is not a full configuration, so you can think of it as the "constraints" used by
   * the {@link ConfigurationMatcher} to produce a full configuration.
   *
   * @return the constraints configuration
   */
  @NonNull
  public FolderConfiguration getEditedConfig() {
    return myEditedConfig;
  }

  /**
   * Sets the associated activity
   *
   * @param activity the activity
   */
  public void setActivity(@Nullable String activity) {
    if (!Objects.equals(myActivity, activity)) {
      myActivity = activity;

      updated(CFG_ACTIVITY);
    }
  }

  /**
   * Sets the device
   *
   * @param device        the device
   * @param preserveState if true, attempt to preserve the state associated with the config
   */
  public void setDevice(Device device, boolean preserveState) {
    if (mySpecificDevice != device) {
      Device prevDevice = mySpecificDevice;
      State prevState = myState;

      myDevice = mySpecificDevice = device;

      int updateFlags = CFG_DEVICE;

      if (device != null) {
        State state = null;
        // Attempt to preserve the device state?
        if (preserveState && prevDevice != null) {
          if (prevState != null) {
            FolderConfiguration oldConfig = DeviceConfigHelper.getFolderConfig(prevState);
            if (oldConfig != null) {
              String stateName = getClosestMatch(oldConfig, device.getAllStates());
              state = device.getState(stateName);
            } else {
              state = device.getState(prevState.getName());
            }
          }
        } else if (preserveState && myStateName != null) {
          state = device.getState(myStateName);
        }
        if (state == null) {
          state = device.getDefaultState();
        }
        if (myState != state) {
          setDeviceStateName(state.getName());
          myState = state;
          updateFlags |= CFG_DEVICE_STATE;
        }
      }

      updated(updateFlags);
    }
  }

  /**
   * Attempts to find a close state among a list
   *
   * @param oldConfig the reference config.
   * @param states    the list of states to search through
   * @return the name of the closest state match, or possibly null if no states are compatible
   *         (this can only happen if the states don't have a single qualifier that is the same).
   */
  @Nullable
  private static String getClosestMatch(@NonNull FolderConfiguration oldConfig, @NonNull List<State> states) {
    // create 2 lists as we're going to go through one and put the
    // candidates in the other.
    List<State> list1 = new ArrayList<>(states.size());
    List<State> list2 = new ArrayList<>(states.size());

    list1.addAll(states);

    final int count = FolderConfiguration.getQualifierCount();
    for (int i = 0; i < count; i++) {
      // compute the new candidate list by only taking states that have
      // the same i-th qualifier as the old state
      for (State s : list1) {
        ResourceQualifier oldQualifier = oldConfig.getQualifier(i);

        FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(s);
        ResourceQualifier newQualifier = folderConfig != null ? folderConfig.getQualifier(i) : null;

        if (oldQualifier == null) {
          if (newQualifier == null) {
            list2.add(s);
          }
        }
        else if (oldQualifier.equals(newQualifier)) {
          list2.add(s);
        }
      }

      // at any moment if the new candidate list contains only one match, its name
      // is returned.
      if (list2.size() == 1) {
        return list2.get(0).getName();
      }

      // if the list is empty, then all the new states failed. It is considered ok, and
      // we move to the next qualifier anyway. This way, if a qualifier is different for
      // all new states it is simply ignored.
      if (!list2.isEmpty()) {
        // move the candidates back into list1.
        list1.clear();
        list1.addAll(list2);
        list2.clear();
      }
    }

    // the only way to reach this point is if there's an exact match.
    // (if there are more than one, then there's a duplicate state and it doesn't matter,
    // we take the first one).
    if (!list1.isEmpty()) {
      return list1.get(0).getName();
    }

    return null;
  }

  /**
   * Sets the device state
   *
   * @param state the device state
   */
  public void setDeviceState(State state) {
    if (myState != state) {
      if (state != null) {
        setDeviceStateName(state.getName());
      } else {
        myStateName = null;
      }
      myState = state;

      updated(CFG_DEVICE_STATE);
    }
  }

  /**
   * Sets the device state name
   *
   * @param stateName the device state name
   */
  public void setDeviceStateName(@Nullable String stateName) {
    ScreenOrientationQualifier qualifier = myEditedConfig.getScreenOrientationQualifier();
    if (qualifier != null) {
      ScreenOrientation orientation = qualifier.getValue();
      if (orientation != null) {
        stateName = orientation.getShortDisplayValue(); // Also used as state names
      }
    }

    if (!Objects.equals(stateName, myStateName)) {
      myStateName = stateName;
      myState = null;

      updated(CFG_DEVICE_STATE);
    }
  }

  /**
   * Sets the locale
   *
   * @param locale the locale
   */
  public void setLocale(@NonNull Locale locale) {
    if (!Objects.equals(myLocale, locale)) {
      myLocale = locale;

      updated(CFG_LOCALE);
    }
  }

  /**
   * Sets the rendering target
   *
   * @param target rendering target
   */
  public void setTarget(@Nullable IAndroidTarget target) {
    if (myTarget != target) {
      myTarget = getTargetForRendering(target, mySettings.getConfigModule());
      updated(CFG_TARGET);
    }
  }

  /**
   * Sets the display name to be shown for this configuration.
   *
   * @param displayName the new display name
   */
  public void setDisplayName(@Nullable String displayName) {
    if (!Objects.equals(myDisplayName, displayName)) {
      myDisplayName = displayName;
      updated(CFG_NAME);
    }
  }

  /**
   * Sets the night mode
   *
   * @param night the night mode
   */
  public void setNightMode(@NonNull NightMode night) {
    if (myNightMode != night) {
      if (night == NightMode.NIGHT) {
        setUiModeFlagValue((getUiModeFlagValue() & UI_MODE_TYPE_MASK) | UI_MODE_NIGHT_YES);
      }
      else {
        setUiModeFlagValue((getUiModeFlagValue() & UI_MODE_TYPE_MASK) | UI_MODE_NIGHT_NO);
      }
    }
  }

  /**
   * Sets the UI mode
   *
   * @param uiMode the UI mode
   */
  public void setUiMode(@NonNull UiMode uiMode) {
    if (myUiMode != uiMode) {
      int newUiTypeFlags = 0;
      switch (uiMode) {
        case NORMAL: newUiTypeFlags = UI_MODE_TYPE_NORMAL; break;
        case DESK: newUiTypeFlags = UI_MODE_TYPE_DESK; break;
        case WATCH: newUiTypeFlags = UI_MODE_TYPE_WATCH; break;
        case TELEVISION: newUiTypeFlags = UI_MODE_TYPE_TELEVISION; break;
        case APPLIANCE: newUiTypeFlags = UI_MODE_TYPE_APPLIANCE; break;
        case CAR: newUiTypeFlags = UI_MODE_TYPE_CAR; break;
        case VR_HEADSET: newUiTypeFlags = UI_MODE_TYPE_VR_HEADSET; break;
      }

      setUiModeFlagValue((getUiModeFlagValue() & UI_MODE_NIGHT_MASK) | newUiTypeFlags);
    }
  }

  /**
   * Sets the raw value for uiMode. When setting it using this method, both UiMode and night mode might be updated as result.
   */
  public void setUiModeFlagValue(int uiMode) {
    int modifiedElements = myUiModeFlagValue ^ uiMode;
    myUiModeFlagValue = uiMode;

    int updatedFlags = 0;

    // Check if we need to update night mode
    if ((modifiedElements & UI_MODE_NIGHT_MASK) != 0) {
      if ((uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
        myNightMode = NightMode.NIGHT;
      }
      else {
        myNightMode = NightMode.NOTNIGHT;
      }
      updatedFlags |= CFG_NIGHT_MODE;
    }

    // Check if we need to update ui mode
    if ((modifiedElements & UI_MODE_TYPE_MASK) != 0) {
      switch (uiMode & UI_MODE_TYPE_MASK) {
        case UI_MODE_TYPE_APPLIANCE: myUiMode = UiMode.APPLIANCE; break;
        case UI_MODE_TYPE_CAR: myUiMode = UiMode.CAR; break;
        case UI_MODE_TYPE_TELEVISION: myUiMode = UiMode.TELEVISION; break;
        case UI_MODE_TYPE_WATCH: myUiMode = UiMode.WATCH; break;
        case UI_MODE_TYPE_DESK: myUiMode = UiMode.DESK; break;
        case UI_MODE_TYPE_VR_HEADSET: myUiMode = UiMode.VR_HEADSET; break;
        default: myUiMode = UiMode.NORMAL;
      }
      updatedFlags |= CFG_UI_MODE;
    }

    if (updatedFlags != 0) {
      updated(updatedFlags);
    }
  }

  /**
   * Returns the current flags for uiMode.
   */
  public int getUiModeFlagValue() {
    return myUiModeFlagValue;
  }

  /**
   * Sets the theme style
   *
   * @param theme the theme
   */
  public void setTheme(@Nullable String theme) {
    if (!Objects.equals(myTheme, theme)) {
      myTheme = theme;
      checkThemePrefix();
      updated(CFG_THEME);
    }
  }

  /**
   * Sets user preference for the scaling factor for fonts, relative to the base density scaling.
   * See {@link android.content.res.Configuration#fontScale}
   *
   * @param fontScale The new scale. Must be greater than 0
   */
  public void setFontScale(float fontScale) {
    assert fontScale > 0f : "fontScale must be greater than 0";

    if (myFontScale != fontScale) {
      myFontScale = fontScale;
      updated(CFG_FONT_SCALE);
    }
  }

  /**
   * Returns user preference for the scaling factor for fonts, relative to the base density scaling.
   * See {@link android.content.res.Configuration#fontScale}
   */
  public float getFontScale() {
    return myFontScale;
  }

  /**
   * Sets the {@link AdaptiveIconShape} to use when rendering
   */
  public void setAdaptiveShape(@NonNull AdaptiveIconShape adaptiveShape) {
    if (myAdaptiveShape != adaptiveShape) {
      myAdaptiveShape = adaptiveShape;
      updated(CFG_ADAPTIVE_SHAPE);
    }
  }

  /**
   * Returns the {@link AdaptiveIconShape} to use when rendering
   */
  @NonNull
  public AdaptiveIconShape getAdaptiveShape() {
    return myAdaptiveShape;
  }

  public void setWallpaper(@Nullable Wallpaper wallpaper) {
    if (!Objects.equals(myWallpaper, wallpaper)) {
      myWallpaper = wallpaper;
      myUseThemedIcon = wallpaper != null;
      updated(CFG_THEME);
    }
  }

  /**
   * Returns the wallpaper resource path to use when rendering
   */
  @Nullable
  public String getWallpaperPath() {
    return myWallpaper != null ? myWallpaper.getResourcePath() : null;
  }

  /**
   * Sets whether the rendering should be edge-to-edge
   */
  public void setEdgeToEdge(boolean edgeToEdge) {
    myEdgeToEdge = edgeToEdge;
  }

  /**
   * Returns whether the rendering should be edge-to-ege
   */
  public boolean isEdgeToEdge() {
    return myEdgeToEdge;
  }

  /**
   * Sets whether the rendering should use the gesture version of the navigation bar
   */
  public void setGestureNav(boolean gestureNav) {
    myGestureNav = gestureNav;
  }

  /**
   * Returns whether the rendering should use the gesture version of the navigation bar
   */
  public boolean isGestureNav() {
    return myGestureNav;
  }

  /**
   * Sets the consumer that applies a transformation function to the rendered image.
   *
   * @param imageTransformation the consumer containing a transformation function to be applied to the rendered image
   */
  public void setImageTransformation(@Nullable Consumer<BufferedImage> imageTransformation) {
    myImageTransformation = imageTransformation;
  }


  /**
   * Returns the transformation function to apply to the rendered image
   *
   * @return the image transformation consumer
   */
  @Nullable
  public Consumer<BufferedImage> getImageTransformation() {
    return myImageTransformation;
  }

  /**
   * Returns whether to use the themed version of adaptive icons
   */
  public boolean getUseThemedIcon() {
    return myUseThemedIcon;
  }

  /**
   * Updates the folder configuration such that it reflects changes in
   * configuration state such as the device orientation, the UI mode, the
   * rendering target, etc.
   */
  protected void syncFolderConfig() {
    Device device = getDevice();
    if (device == null) {
      return;
    }

    // get the device config from the device/state combos.
    State deviceState = getDeviceState();
    if (deviceState == null) {
      deviceState = device.getDefaultState();
    }
    FolderConfiguration config = getFolderConfig(mySettings.getConfigModule(), deviceState, getLocale(), getTarget());

    // replace the config with the one from the device
    myFullConfig.set(config);

    // sync the selected locale
    Locale locale = getLocale();
    myFullConfig.setLocaleQualifier(locale.qualifier);
    LayoutDirectionQualifier layoutDirectionQualifier = myEditedConfig.getLayoutDirectionQualifier();
    if (layoutDirectionQualifier != null && layoutDirectionQualifier != layoutDirectionQualifier.getNullQualifier()) {
      myFullConfig.setLayoutDirectionQualifier(layoutDirectionQualifier);
    } else if (!locale.hasLanguage()) {
      // Avoid getting the layout library if the locale doesn't have any language.
      myFullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
    } else {
      ConfigurationModelModule configModule = mySettings.getConfigModule();
      LayoutLibrary layoutLib = getLayoutLibrary(getTarget(), configModule.getAndroidPlatform(), configModule.getLayoutlibContext());
      if (layoutLib != null) {
        if (layoutLib.isRtl(locale.toLocaleId())) {
          myFullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
        } else {
          myFullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
        }
      }
    }

    // Replace the UiMode with the selected one, if one is selected
    UiMode uiMode = getUiMode();
    myFullConfig.setUiModeQualifier(new UiModeQualifier(uiMode));

    // Replace the NightMode with the selected one, if one is selected
    NightMode nightMode = getNightMode();
    myFullConfig.setNightModeQualifier(new NightModeQualifier(nightMode));

    // replace the API level by the selection of the combo
    IAndroidTarget target = getTarget();
    if (target != null) {
      int apiLevel = target.getVersion().getFeatureLevel();
      myFullConfig.setVersionQualifier(new VersionQualifier(apiLevel));
    }

    myFolderConfigDirty = 0;
    myProjectStateVersion = mySettings.getStateVersion();
  }

  /** Returns the screen size required for this configuration */
  @Nullable
  public ScreenSize getScreenSize() {
    // Look up the screen size for the current state

    State deviceState = getDeviceState();
    if (deviceState != null) {
      FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(deviceState);
      if (folderConfig != null) {
        ScreenSizeQualifier qualifier = folderConfig.getScreenSizeQualifier();
        assert qualifier != null;
        return qualifier.getValue();
      }
    }

    ScreenSize screenSize = null;
    Device device = getDevice();
    if (device != null) {
      List<State> states = device.getAllStates();
      for (State state : states) {
        FolderConfiguration folderConfig = DeviceConfigHelper.getFolderConfig(state);
        if (folderConfig != null) {
          ScreenSizeQualifier qualifier = folderConfig.getScreenSizeQualifier();
          assert qualifier != null;
          screenSize = qualifier.getValue();
          break;
        }
      }
    }

    return screenSize;
  }

  private void checkThemePrefix() {
    if (myTheme != null && !myTheme.startsWith(PREFIX_RESOURCE_REF)) {
      if (myTheme.isEmpty()) {
        myTheme = getPreferredTheme();
        return;
      }

      myTheme = ResourceUtils.getStyleResourceUrl(myTheme);
    }
  }

  /**
   * Returns the currently selected {@link Density}. This is guaranteed to be non null.
   *
   * @return the density
   */
  @NonNull
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
    Device device = getDevice();
    if (device == null) {
      return null;
    }
    List<State> states = device.getAllStates();
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i) == from) {
        return states.get((i + 1) % states.size());
      }
    }

    // Search by name instead
    if (from != null) {
      String name = from.getName();
      for (int i = 0; i < states.size(); i++) {
        if (states.get(i).getName().equals(name)) {
          return states.get((i + 1) % states.size());
        }
      }
    }

    return null;
  }

  /**
   * Marks the beginning of a "bulk" editing operation with repeated calls to
   * various setters. After all the values have been set, the client <b>must</b>
   * call {@link #finishBulkEditing()}. This allows configurations to avoid
   * doing {@link FolderConfiguration} syncing for intermediate stages, and all
   * listener updates are deferred until the bulk operation is complete.
   */
  public void startBulkEditing() {
    synchronized (this) {
      myBulkEditingCount++;
    }
  }

  /**
   * Marks the end of a "bulk" editing operation. At this point listeners will
   * be notified of the cumulative changes, etc. See {@link #startBulkEditing()}
   * for details.
   */
  public void finishBulkEditing() {
    boolean notify = false;
    synchronized (this) {
      myBulkEditingCount--;
      if (myBulkEditingCount == 0) {
        notify = true;
      }
    }

    if (notify) {
      updated(0);
    }
  }

  /** Called when one or more attributes of the configuration has changed */
  public void updated(int flags) {
    myNotifyDirty |= flags;
    myFolderConfigDirty |= flags;
    myModificationCount++;

    if (myBulkEditingCount == 0) {
      int changed = myNotifyDirty;
      if (myListeners != null) {
        for (ConfigurationListener listener : myListeners) {
          listener.changed(changed);
        }
      }

      myNotifyDirty = 0;
    }
  }

  /**
   * Adds a listener to be notified when the configuration changes
   *
   * @param listener the listener to add
   */
  public void addListener(@NonNull ConfigurationListener listener) {
    if (myListeners == null) {
      myListeners = new ArrayList<>();
    }
    myListeners.add(listener);
  }

  /**
   * Removes a listener such that it is no longer notified of changes
   *
   * @param listener the listener to remove
   */
  public void removeListener(@NonNull ConfigurationListener listener) {
    if (myListeners != null) {
      myListeners.remove(listener);
      if (myListeners.isEmpty()) {
        myListeners = null;
      }
    }
  }

  // ---- Resolving resources ----

  @Slow
  public @NonNull ResourceResolver getResourceResolver() {
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

  @NonNull
  public List<FrameworkOverlay> getOverlays() {
    return myGestureNav ? List.of(FrameworkOverlay.NAV_GESTURE) : List.of(FrameworkOverlay.NAV_3_BUTTONS);
  }

  // For debugging only
  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this.getClass())
      .add("display", myDisplayName)
      .add("theme", myTheme)
      .add("activity", myActivity)
      .add("device", myDevice)
      .add("state", myState)
      .add("locale", myLocale)
      .add("target", myTarget)
      .add("uimode", myUiMode)
      .add("nightmode", myNightMode)
      .toString();
  }

  @NonNull
  public ConfigurationModelModule getConfigModule() {
    return mySettings.getConfigModule();
  }

  public void setEffectiveDevice(@Nullable Device device, @Nullable State state) {
    int updateFlags = 0;
    if (myDevice != device) {
      updateFlags = CFG_DEVICE;
      myDevice = device;
    }

    if (myState != state) {
      myState = state;
      myStateName = state != null ? state.getName() : null;
      updateFlags |= CFG_DEVICE_STATE;
    }

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
  private static IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target, @NonNull ConfigurationModelModule module) {
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
  @NonNull
  public String getPreferredTheme() {
    return mySettings.getConfigModule().getThemeInfoProvider().getDefaultTheme(this);
  }
}
