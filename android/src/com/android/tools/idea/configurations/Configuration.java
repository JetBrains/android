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

import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_ACTIVITY;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_ADAPTIVE_SHAPE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_DEVICE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_DEVICE_STATE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_FONT_SCALE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_LOCALE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_NAME;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_TARGET;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_THEME;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_UI_MODE;
import static com.android.tools.idea.configurations.ConfigurationListener.MASK_FOLDERCONFIG;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceRepository;
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
import com.android.resources.FolderTypeRelationship;
import com.android.resources.LayoutDirection;
import com.android.resources.NightMode;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.StudioRenderServiceKt;
import com.android.tools.idea.res.ResourceFilesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.ResourceUtils;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@linkplain Configuration} is a selection of device, orientation, theme,
 * etc for use when rendering a layout.
 */
public class Configuration implements Disposable, ModificationTracker {
  public static final String AVD_ID_PREFIX = "_android_virtual_device_id_";
  public static final String CUSTOM_DEVICE_ID = "Custom";

  // Set of constants from {@link android.content.res.Configuration} to be used in setUiModeFlagValue.
  private static final int UI_MODE_TYPE_MASK = 0x0000000f;
  private static final int UI_MODE_TYPE_APPLIANCE = 0x00000005;
  private static final int UI_MODE_TYPE_CAR = 0x00000003;
  private static final int UI_MODE_TYPE_DESK = 0x00000002;
  private static final int UI_MODE_TYPE_NORMAL = 0x00000001;
  private static final int UI_MODE_TYPE_TELEVISION = 0x00000004;
  private static final int UI_MODE_TYPE_VR_HEADSET = 0x00000007;
  private static final int UI_MODE_TYPE_WATCH = 0x00000006;

  private static final int UI_MODE_NIGHT_MASK = 0x00000030;
  private static final int UI_MODE_NIGHT_YES = 0x00000020;
  private static final int UI_MODE_NIGHT_NO = 0x00000010;

  /**
   * The associated file.
   * TODO(b/141988340): consider to remove this field from Configuration class.
   */
  @Nullable final VirtualFile myFile;

  /** The PSI File associated with myFile. */
  @Nullable private PsiFile myPsiFile;

  /**
   * The {@link FolderConfiguration} representing the state of the UI controls
   */
  @NotNull
  protected final FolderConfiguration myFullConfig = new FolderConfiguration();

  /** The associated {@link ConfigurationManager} */
  @NotNull
  protected final ConfigurationManager myManager;

  /**
   * The {@link FolderConfiguration} being edited.
   */
  @NotNull
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
  @NotNull
  private UiMode myUiMode = UiMode.NORMAL;

  /**
   * Night mode
   */
  @NotNull
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
  @NotNull private AdaptiveIconShape myAdaptiveShape = AdaptiveIconShape.getDefaultShape();
  private boolean myUseThemedIcon = false;
  private String myWallpaperPath = null;

  /**
   * Creates a new {@linkplain Configuration}
   */
  protected Configuration(@NotNull ConfigurationManager manager, @Nullable VirtualFile file, @NotNull FolderConfiguration editedConfig) {
    myManager = manager;
    myFile = file;
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
  @NotNull
  public static Configuration create(@NotNull ConfigurationManager manager,
                                     @Nullable VirtualFile file,
                                     @NotNull FolderConfiguration editedConfig) {
    return new Configuration(manager, file, editedConfig);
  }

  /**
   * Creates a configuration suitable for the given file.
   *
   * @param base the base configuration to base the file configuration off of
   * @param file the file to look up a configuration for
   * @return a suitable configuration
   */
  @NotNull
  public static Configuration create(@NotNull Configuration base, @NotNull VirtualFile file) {
    // TODO: Figure out whether we need this, or if it should be replaced by a call to ConfigurationManager#createSimilar()
    Configuration configuration = copyWithNewFile(base, file);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, file);
    configuration.getEditedConfig().set(FolderConfiguration.getConfigForFolder(file.getParent().getName()));
    matcher.adaptConfigSelection(true /*needBestMatch*/);

    return configuration;
  }

  @NotNull
  public static Configuration create(@NotNull ConfigurationManager manager,
                                     @Nullable VirtualFile file,
                                     @Nullable ConfigurationFileState fileState,
                                     @NotNull FolderConfiguration editedConfig) {
    Configuration configuration = new Configuration(manager, file, editedConfig);

    configuration.startBulkEditing();
    if (fileState != null) {
      fileState.loadState(configuration);
    }
    configuration.finishBulkEditing();

    return configuration;
  }

  /**
   * Creates a new {@linkplain Configuration} that is a copy from a different configuration.
   *
   * @param original the original to copy from
   * @return a new configuration copied from the original
   */
  @NotNull
  public static Configuration copy(@NotNull Configuration original) {
    return copyWithNewFile(original, original.myFile);
  }

  /**
   * Creates a new {@linkplain Configuration} that is a copy from a different configuration and its associated file
   * is the given one.
   *
   * @param original the original to copy from
   * @param newFile  the file of returned {@link Configuration}.
   * @return a new configuration copied from the original
   */
  @NotNull
  private static Configuration copyWithNewFile(@NotNull Configuration original, @Nullable VirtualFile newFile) {
    FolderConfiguration copiedConfig = new FolderConfiguration();
    copiedConfig.set(original.getEditedConfig());
    Configuration copy = new Configuration(original.myManager, newFile, copiedConfig);
    copy.myFullConfig.set(original.myFullConfig);
    copy.myFolderConfigDirty = original.myFolderConfigDirty;
    copy.myProjectStateVersion = original.myProjectStateVersion;
    copy.myTarget = original.myTarget; // avoid getTarget() since it fetches project state
    copy.myLocale = original.myLocale;  // avoid getLocale() since it fetches project state
    copy.myTheme = original.getTheme();
    copy.mySpecificDevice = original.mySpecificDevice;
    copy.myDevice = original.myDevice; // avoid getDevice() since it fetches project state
    copy.myStateName = original.myStateName;
    copy.myState = original.myState;
    copy.myActivity = original.getActivity();
    copy.myUiMode = original.getUiMode();
    copy.myNightMode = original.getNightMode();
    copy.myDisplayName = original.getDisplayName();
    copy.myFontScale = original.myFontScale;
    copy.myUiModeFlagValue = original.myUiModeFlagValue;
    copy.myAdaptiveShape = original.myAdaptiveShape;
    copy.myUseThemedIcon = original.myUseThemedIcon;
    copy.myWallpaperPath = original.myWallpaperPath;

    return copy;
  }

  @Override
  public Configuration clone() {
    return copy(this);
  }

  public void save() {
    ConfigurationStateManager stateManager = myManager.getConfigModule().getConfigurationStateManager();

    if (myFile != null) {
      ConfigurationFileState fileState = new ConfigurationFileState();
      fileState.saveState(this);
      stateManager.setConfigurationState(myFile, fileState);
    }
  }

  /**
   * Returns the associated {@link ConfigurationManager}
   *
   * @return the manager
   */
  @NotNull
  public ConfigurationManager getConfigurationManager() {
    return myManager;
  }

  /**
   * Returns the file associated with this configuration, if any
   *
   * @return the file, or null
   */
  @Nullable
  public VirtualFile getFile() {
    return myFile;
  }

  /**
   * Returns the PSI file associated with the configuration, if any
   */
  @Nullable
  public PsiFile getPsiFile() {
    if (myPsiFile == null && myFile != null) {
      myPsiFile = AndroidPsiUtils.getPsiFileSafely(myManager.getProject(), myFile);
    }
    return myPsiFile;
  }

  /**
   * Returns the associated activity
   *
   * @return the activity
   */
  @Nullable
  public String getActivity() {
    if (myActivity == NO_ACTIVITY) {
      return null;
    } else if (myActivity == null && myFile != null) {
      myActivity = ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
        if (myPsiFile == null) {
          myPsiFile = PsiManager.getInstance(myManager.getProject()).findFile(myFile);
        }
        if (myPsiFile instanceof XmlFile) {
          XmlFile xmlFile = (XmlFile)myPsiFile;
          XmlTag rootTag = xmlFile.getRootTag();
          if (rootTag != null) {
            XmlAttribute attribute = rootTag.getAttribute(ATTR_CONTEXT, TOOLS_URI);
            if (attribute != null) {
              return attribute.getValue();
            }
          }

        }
        return null;
      });
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
  public static FolderConfiguration getFolderConfig(@NotNull Module module, @NotNull State state, @NotNull Locale locale,
                                                    @Nullable IAndroidTarget target) {
    FolderConfiguration currentConfig = DeviceConfigHelper.getFolderConfig(state);
    if (currentConfig != null) {
      if (locale.hasLanguage()) {
        currentConfig.setLocaleQualifier(locale.qualifier);

        if (locale.hasLanguage()) {
          LayoutLibrary layoutLib = StudioRenderServiceKt.getLayoutLibrary(module, target);
          if (layoutLib != null) {
            if (layoutLib.isRtl(locale.toLocaleId())) {
              currentConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
            }
          }
        }
      }
    }

    return currentConfig;
  }

  @Slow
  @Nullable
  private Device computeBestDevice() {
    for (Device device : myManager.getRecentDevices(DeviceUtils.getAvdDevices(this))) {
      String stateName = myStateName;
      if (stateName == null) {
        stateName = device.getDefaultState().getName();
      }
      State selectedState = ConfigurationFileState.getState(device, stateName);
      Module module = myManager.getModule();
      FolderConfiguration currentConfig = getFolderConfig(module, selectedState, getLocale(), getTarget());
      if (currentConfig != null) {
        if (myEditedConfig.isMatchFor(currentConfig)) {
          ResourceRepositoryManager repositoryManager = myManager.getConfigModule().getResourceRepositoryManager();
          if (repositoryManager != null && myFile != null) {
            ResourceFolderType folderType = ResourceFilesUtil.getFolderType(myFile);
            if (folderType != null) {
              if (ResourceFolderType.VALUES.equals(folderType)) {
                // If it's a file in the values folder, ResourceRepository.getMatchingFiles won't work.
                // We get instead all the available folders and check that there is one compatible.
                LocalResourceManager resourceManager = LocalResourceManager.getInstance(module);
                if (resourceManager != null) {
                  for (PsiFile resourceFile : resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.VALUES)) {
                    if (!myFile.equals(resourceFile.getVirtualFile())) continue;
                    PsiDirectory parent = AndroidPsiUtils.getPsiDirectorySafely(resourceFile);
                    if (parent != null) {
                      FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(parent.getName());
                      if (currentConfig.isMatchFor(folderConfiguration)) {
                        return device;
                      }
                    }
                  }
                }
              } else {
                List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
                if (!types.isEmpty()) {
                  ResourceType type = types.get(0);
                  ResourceRepository resources = repositoryManager.getAppResources();
                  List<VirtualFile> matches =
                      ConfigurationMatcher.getMatchingFiles(resources, myFile, ResourceNamespace.TODO(), type, currentConfig);
                  if (matches.contains(myFile)) {
                    return device;
                  }
                }
              }
            } else if ("Kotlin".equals(myFile.getFileType().getName())) {
              return device;
            } else if (myFile.equals(myManager.getProject().getProjectFile())) {
              return device;              // Takes care of correct device selection for Theme Editor.
            }
          }
        }
      }
    }

    return myManager.getDefaultDevice();
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
      myState = ConfigurationFileState.getState(device, myStateName);
    }

    return myState;
  }

  /**
   * Returns the chosen locale
   *
   * @return the locale
   */
  @NotNull
  public Locale getLocale() {
    if (myLocale == null) {
      return myManager.getLocale();
    }
    return myLocale;
  }

  /**
   * Returns the UI mode
   *
   * @return the UI mode
   */
  @NotNull
  public UiMode getUiMode() {
    return myUiMode;
  }

  /**
   * Returns the day/night mode
   *
   * @return the night mode
   */
  @NotNull
  public NightMode getNightMode() {
    return myNightMode;
  }

  /**
   * Returns the current theme style name, in the form @style/ThemeName or @android:style/ThemeName
   *
   * @return the theme style name
   */
  @NotNull
  public String getTheme() {
    if (myTheme == null) {
      myTheme = myManager.computePreferredTheme(this);
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
      IAndroidTarget target = myManager.getTarget();

      // If the project-wide render target isn't a match for the version qualifier in this layout
      // (for example, the render target is at API 11, and layout is in a -v14 folder) then pick
      // a target which matches.
      VersionQualifier version = myEditedConfig.getVersionQualifier();
      if (target != null && version != null && version.getVersion() > target.getVersion().getFeatureLevel()) {
        target = myManager.getTarget(version.getVersion());
      }

      return getTargetForRendering(target);
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
  @NotNull
  public FolderConfiguration getFullConfig() {
    if ((myFolderConfigDirty & MASK_FOLDERCONFIG) != 0 || myProjectStateVersion != myManager.getStateVersion()) {
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
    if (!StringUtil.equals(myActivity, activity)) {
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
  private static String getClosestMatch(@NotNull FolderConfiguration oldConfig, @NotNull List<State> states) {
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

    if (!Objects.equal(stateName, myStateName)) {
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
  public void setLocale(@NotNull Locale locale) {
    if (!Objects.equal(myLocale, locale)) {
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
      myTarget = getTargetForRendering(target);
      updated(CFG_TARGET);
    }
  }

  /**
   * Sets the display name to be shown for this configuration.
   *
   * @param displayName the new display name
   */
  public void setDisplayName(@Nullable String displayName) {
    if (!StringUtil.equals(myDisplayName, displayName)) {
      myDisplayName = displayName;
      updated(CFG_NAME);
    }
  }

  /**
   * Sets the night mode
   *
   * @param night the night mode
   */
  public void setNightMode(@NotNull NightMode night) {
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
  public void setUiMode(@NotNull UiMode uiMode) {
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
    if (!StringUtil.equals(myTheme, theme)) {
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
  public void setAdaptiveShape(@NotNull AdaptiveIconShape adaptiveShape) {
    if (myAdaptiveShape != adaptiveShape) {
      myAdaptiveShape = adaptiveShape;
      updated(CFG_ADAPTIVE_SHAPE);
    }
  }

  /**
   * Returns the {@link AdaptiveIconShape} to use when rendering
   */
  @NotNull
  public AdaptiveIconShape getAdaptiveShape() {
    return myAdaptiveShape;
  }

  public void setWallpaperPath(@Nullable String wallpaperPath) {
    if (!Objects.equal(myWallpaperPath, wallpaperPath)) {
      myWallpaperPath = wallpaperPath;
      updated(CFG_THEME);
    }
  }

  /**
   * Returns the wallpaper resource path to use when rendering
   */
  @Nullable
  public String getWallpaperPath() {
    return myWallpaperPath;
  }

  public void setUseThemedIcon(boolean useThemedIcon) {
    if (myUseThemedIcon != useThemedIcon) {
      myUseThemedIcon = useThemedIcon;
      updated(CFG_THEME);
    }
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
    FolderConfiguration config = getFolderConfig(getModule(), deviceState, getLocale(), getTarget());

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
      LayoutLibrary layoutLib = StudioRenderServiceKt.getLayoutLibrary(getModule(), getTarget());
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
    myProjectStateVersion = myManager.getStateVersion();
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
        myTheme = myManager.computePreferredTheme(this);
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
  public void addListener(@NotNull ConfigurationListener listener) {
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
  public void removeListener(@NotNull ConfigurationListener listener) {
    if (myListeners != null) {
      myListeners.remove(listener);
      if (myListeners.isEmpty()) {
        myListeners = null;
      }
    }
  }

  // ---- Resolving resources ----

  @Slow
  public @NotNull ResourceResolver getResourceResolver() {
    String theme = getTheme();
    Device device = getDevice();
    ResourceResolverCache resolverCache = myManager.getResolverCache();
    if (device != null && CUSTOM_DEVICE_ID.equals(device.getId())) {
      // Remove the old custom device configuration only if it's different from the new one
      resolverCache.replaceCustomConfig(theme, getFullConfig());
    }
    return resolverCache.getResourceResolver(getTarget(), theme, getFullConfig());
  }

  /**
   * Returns an {@link ResourceRepository} for the framework resources based on the current
   * configuration selection.
   *
   * @return the framework resources or null if not found.
   */
  @Nullable
  public ResourceRepository getFrameworkResources() {
    IAndroidTarget target = getTarget();
    if (target != null) {
      return myManager.getResolverCache().getFrameworkResources(getFullConfig(), target);
    }

    return null;
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

  @NotNull
  public Module getModule() {
    return myManager.getModule();
  }

  @Override
  public void dispose() {
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

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  /**
   * Returns a target that is only suitable to be used for rendering (as opposed to a target that can be used for attribute resolution).
   */
  @Nullable
  private static IAndroidTarget getTargetForRendering(@Nullable IAndroidTarget target) {
    if (target == null) {
      return null;
    }

    return StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
  }
}
