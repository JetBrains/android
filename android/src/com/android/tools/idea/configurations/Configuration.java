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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.base.Objects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.configurations.ConfigurationListener.*;

/**
 * A {@linkplain Configuration} is a selection of device, orientation, theme,
 * etc for use when rendering a layout.
 */
public class Configuration implements Disposable, ModificationTracker {

  /** Min API version that supports preferences API rendering. */
  public static final int PREFERENCES_MIN_API = 22;

  /** The associated file */
  @Nullable final VirtualFile myFile;

  /** The PSI File associated with myFile. */
  @Nullable private PsiFile myPsiFile;

  /**
   * The {@link com.android.ide.common.resources.configuration.FolderConfiguration} representing the state of the UI controls
   */
  @NotNull
  protected final FolderConfiguration myFullConfig = new FolderConfiguration();

  /** The associated {@link ConfigurationManager} */
  @NotNull
  protected final ConfigurationManager myManager;

  /**
   * The {@link com.android.ide.common.resources.configuration.FolderConfiguration} being edited.
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
   * {@link com.android.sdklib.devices.State#getHardware()} accessor).
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

    if (file != null) {
      if (ResourceHelper.getFolderType(file) == ResourceFolderType.XML) {
        myPsiFile = AndroidPsiUtils.getPsiFileSafely(manager.getProject(), file);
        if (myPsiFile != null && TAG_PREFERENCE_SCREEN.equals(AndroidPsiUtils.getRootTagName(myPsiFile))) {
          myTarget = manager.getTarget(PREFERENCES_MIN_API);
        }
      }
    }
  }

  /**
   * Creates a new {@linkplain Configuration}
   *
   * @return a new configuration
   */
  @NotNull
  @VisibleForTesting
  static Configuration create(@NotNull ConfigurationManager manager,
                              @Nullable VirtualFile file,
                              @NotNull FolderConfiguration editedConfig) {
    return new Configuration(manager, file, editedConfig);
  }

  /**
   * Creates a configuration suitable for the given file
   *
   * @param base the base configuration to base the file configuration off of
   * @param file the file to look up a configuration for
   * @return a suitable configuration
   */
  @NotNull
  public static Configuration create(@NotNull Configuration base,
                                     @NotNull VirtualFile file) {
    // TODO: Figure out whether we need this, or if it should be replaced by
    // a call to ConfigurationManager#createSimilar()
    Configuration configuration = base.clone();
    LocalResourceRepository resources = AppResourceRepository.getAppResources(base.getModule(), true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, resources, file);
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
   * Creates a new {@linkplain Configuration} that is a copy from a different configuration
   *
   * @param original the original to copy from
   * @return a new configuration copied from the original
   */
  @NotNull
  public static Configuration copy(@NotNull Configuration original) {
    FolderConfiguration copiedConfig = new FolderConfiguration();
    copiedConfig.set(original.getEditedConfig());
    Configuration copy = new Configuration(original.myManager, original.myFile, copiedConfig);
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

    return copy;
  }

  @Override
  public Configuration clone() {
    return copy(this);
  }

  /**
   * Copies attributes from the given source configuration into the given destination configuration,
   * as long as the attributes are compatible with the folder of the destination file.
   *
   * @param source the original to copy from
   * @return a new configuration copied from the original
   */
  @NotNull
  public static Configuration copyCompatible(@NotNull Configuration source, @NotNull Configuration destination) {
    assert !Comparing.equal(source.myFile, destination.myFile); // This method is intended to sync configurations for resource variations

    FolderConfiguration editedConfig = destination.getEditedConfig();

    if (editedConfig.getVersionQualifier() == null) {
      destination.myTarget = source.myTarget;  // avoid getTarget() since it fetches project state
    }
    if (editedConfig.getScreenSizeQualifier() == null) {
      destination.mySpecificDevice = source.mySpecificDevice; // avoid getDevice() since it fetches project state
    }
    if (editedConfig.getScreenOrientationQualifier() == null && editedConfig.getSmallestScreenWidthQualifier() == null) {
      destination.myStateName = source.myStateName;
      destination.myState = source.myState;
    }
    if (editedConfig.getLocaleQualifier() == null) {
      destination.myLocale = source.myLocale; // avoid getLocale() since it fetches project state
    }
    if (editedConfig.getUiModeQualifier() == null) {
      destination.myUiMode = source.getUiMode();
    }
    if (editedConfig.getNightModeQualifier() == null) {
      destination.myNightMode = source.getNightMode();
    }
    destination.myActivity = source.getActivity();
    destination.myTheme = source.getTheme();
    //destination.myDisplayName = source.getDisplayName();

    LocalResourceRepository resources = AppResourceRepository.getAppResources(source.myManager.getModule(), true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(destination, resources, destination.myFile);
    //if (!matcher.isCurrentFileBestMatchFor(editedConfig)) {
      matcher.adaptConfigSelection(true /*needBestMatch*/);
    //}

    return destination;
  }


  public void save() {
    ConfigurationStateManager stateManager = ConfigurationStateManager.get(myManager.getModule().getProject());

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
      myActivity = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
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
        }
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
   * Returns the chosen device.
   *
   * @return the chosen device
   */
  @Nullable
  public Device getDevice() {
    if (myDevice == null) {
      if (mySpecificDevice != null) {
        myDevice = mySpecificDevice;
      }
      else {
        myDevice = computeBestDevice();
      }
    }

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
          LayoutLibrary layoutLib = RenderService.getLayoutLibrary(module, target);
          if (layoutLib != null) {
            if (layoutLib.isRtl(locale.toLocaleId())) {
              currentConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
            }
          }
        }
      }

      // Don't match on target since we tend to use recent layout lib versions to render even default (older) layouts
      // since more recent versions work a lot better fidelity wise
      // if (target != null) {
      //   currentConfig.setVersionQualifier(new VersionQualifier(target.getVersion().getApiLevel()));
      // }
    }

    return currentConfig;
  }

  @Nullable
  private Device computeBestDevice() {
    for (Device device : myManager.getRecentDevices()) {
      String stateName = myStateName;
      if (stateName == null) {
        stateName = device.getDefaultState().getName();
      }
      State selectedState = ConfigurationFileState.getState(device, stateName);
      Module module = myManager.getModule();
      FolderConfiguration currentConfig = getFolderConfig(module, selectedState, getLocale(), getTarget());
      if (currentConfig != null) {
        if (myEditedConfig.isMatchFor(currentConfig)) {
          LocalResourceRepository resources = AppResourceRepository.getAppResources(module, true);
          if (resources != null && myFile != null) {
            ResourceFolderType folderType = ResourceHelper.getFolderType(myFile);
            if (folderType != null) {
              if (ResourceFolderType.VALUES.equals(folderType)) {
                // If it's a file in the values folder, ResourceRepository.getMatchingFiles won't work.
                // We get instead all the available folders and check that there is one compatible.
                LocalResourceManager resourceManager = LocalResourceManager.getInstance(module);
                if (resourceManager != null) {
                  for (PsiFile resourceFile : resourceManager.findResourceFiles("values")) {
                    if (myFile.equals(resourceFile.getVirtualFile()) && resourceFile.getParent() != null) {
                      FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(resourceFile.getParent().getName());
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
                  List<VirtualFile> matches = resources.getMatchingFiles(myFile, type, currentConfig);
                  if (matches.contains(myFile)) {
                    return device;
                  }
                }
              }
            } else if ("Kotlin".equals(myFile.getFileType().getName())) {
              return device;
            } else if (myFile.equals(myManager.getProject().getProjectFile())) {
              return device;              // takes care of correct device selection for Theme Editor
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
  @Nullable
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
        return myManager.getTarget(version.getVersion());
      }

      return target;
    }

    return myTarget;
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
   * Returns the full, complete {@link com.android.ide.common.resources.configuration.FolderConfiguration}
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
   * Copies the full, complete {@link com.android.ide.common.resources.configuration.FolderConfiguration} into the given
   * folder config instance.
   *
   * @param dest the {@link com.android.ide.common.resources.configuration.FolderConfiguration} instance to copy into
   */
  public void copyFullConfig(FolderConfiguration dest) {
    dest.set(myFullConfig);
  }

  /**
   * Returns the edited {@link com.android.ide.common.resources.configuration.FolderConfiguration} (this is not a full
   * configuration, so you can think of it as the "constraints" used by the
   * {@link ConfigurationMatcher} to produce a full configuration.
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

      // TODO: Is this redundant with the stuff above?
      if (mySpecificDevice != null && myState == null) {
        setDeviceStateName(mySpecificDevice.getDefaultState().getName());
        myState = mySpecificDevice.getDefaultState();
        updateFlags |= CFG_DEVICE_STATE;
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
    List<State> list1 = new ArrayList<State>(states.size());
    List<State> list2 = new ArrayList<State>(states.size());

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
      if (list2.size() != 0) {
        // move the candidates back into list1.
        list1.clear();
        list1.addAll(list2);
        list2.clear();
      }
    }

    // the only way to reach this point is if there's an exact match.
    // (if there are more than one, then there's a duplicate state and it doesn't matter,
    // we take the first one).
    if (list1.size() > 0) {
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
      myTarget = target;
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
      myNightMode = night;

      updated(CFG_NIGHT_MODE);
    }
  }

  /**
   * Sets the UI mode
   *
   * @param uiMode the UI mode
   */
  public void setUiMode(@NotNull UiMode uiMode) {
    if (myUiMode != uiMode) {
      myUiMode = uiMode;

      updated(CFG_UI_MODE);
    }
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
    if (myEditedConfig.getLayoutDirectionQualifier() != null) {
      myFullConfig.setLayoutDirectionQualifier(myEditedConfig.getLayoutDirectionQualifier());
    } else if (!locale.hasLanguage()) {
      // Avoid getting the layout library if the locale doesn't have any language.
      myFullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
    } else {
      LayoutLibrary layoutLib = RenderService.getLayoutLibrary(getModule(), getTarget());
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

      myTheme = ResolutionUtils.getStyleResourceUrl(myTheme);
    }
  }

  /**
   * Returns the currently selected {@link com.android.resources.Density}. This is guaranteed to be non null.
   *
   * @return the density
   */
  @NotNull
  public Density getDensity() {
    DensityQualifier qualifier = myFullConfig.getDensityQualifier();
    if (qualifier != null) {
      // just a sanity check
      Density d = qualifier.getValue();
      if (d.isValidValueForDevice()) {
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
   * Returns true if this configuration supports the given rendering
   * capability
   *
   * @param capability the capability to check
   * @return true if the capability is supported
   */
  public boolean supports(@MagicConstant(flagsFromClass = Features.class) int capability) {
    IAndroidTarget target = getTarget();
    if (target != null) {
      return RenderService.supportsCapability(getModule(), target, capability);
    }

    return false;
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

    if (myManager.getStateVersion() != myProjectStateVersion) {
      myNotifyDirty |= MASK_PROJECT_STATE;
      myFolderConfigDirty |= MASK_PROJECT_STATE;
      myDevice = null;
      myState = null;
    }

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
      myListeners = new ArrayList<ConfigurationListener>();
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

  @Nullable
  public ResourceResolver getResourceResolver() {
    String theme = getTheme();
    if (theme != null) {
      return myManager.getResolverCache().getResourceResolver(getTarget(), theme, getFullConfig());
    }

    return null;
  }

  /**
   * Returns a {@link LocalResourceRepository} for the framework resources based on the current
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
    return Objects.toStringHelper(this.getClass())
      .add("display", getDisplayName())
      .add("theme", getTheme())
      .add("activity", getActivity())
      .add("device", getDevice())
      .add("state", getDeviceState())
      .add("locale", getLocale())
      .add("target", getTarget())
      .add("uimode", getUiMode())
      .add("nightmode", getNightMode())
      .toString();
  }

  public Module getModule() {
    return myManager.getModule();
  }

  public boolean isBestMatchFor(VirtualFile file, FolderConfiguration config) {
    LocalResourceRepository resources = AppResourceRepository.getAppResources(getModule(), true);
    return new ConfigurationMatcher(this, resources, file).isCurrentFileBestMatchFor(config);
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
}
