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
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.*;
import com.google.common.base.Objects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.configurations.ConfigurationListener.*;

/**
 * A {@linkplain Configuration} is a selection of device, orientation, theme,
 * etc for use when rendering a layout.
 */
public class Configuration implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.configurations.Configuration");

  /** The associated file */
  @Nullable VirtualFile myFile;

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
   * The device to render with
   */
  @Nullable
  private Device myDevice;

  /**
   * The device state
   */
  @Nullable
  private State myState;

  /**
   * The activity associated with the layout. This is just a cached value of
   * the true value stored on the layout.
   */
  @Nullable
  private String myActivity;

  /**
   * The locale to use for this configuration
   */
  @NotNull
  private Locale myLocale = Locale.ANY;

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

  /**
   * Creates a new {@linkplain Configuration}
   */
  protected Configuration(@NotNull ConfigurationManager manager, @Nullable VirtualFile file, @NotNull FolderConfiguration editedConfig) {
    myManager = manager;
    myFile = file;
    myEditedConfig = editedConfig;
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
    Configuration configuration = new Configuration(manager, file, editedConfig);
    configuration.myDevice = manager.getDefaultDevice();
    assert configuration.ensureValid();
    return configuration;
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
    ProjectResources resources = ProjectResources.get(base.getModule(), true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, resources, file);
    configuration.getEditedConfig().set(FolderConfiguration.getConfigForFolder(file.getParent().getName()));
    matcher.adaptConfigSelection(true /*needBestMatch*/);

    return configuration;
  }

  @NotNull
  public static Configuration create(@NotNull ConfigurationManager manager,
                                     @Nullable ConfigurationProjectState projectState,
                                     @Nullable VirtualFile file,
                                     @Nullable ConfigurationFileState fileState,
                                     @NotNull FolderConfiguration editedConfig) {
    Configuration configuration = new Configuration(manager, file, editedConfig);

    configuration.startBulkEditing();
    if (projectState != null) {
      projectState.loadState(configuration);
    } else {
      configuration.myTarget = manager.getDefaultTarget();
    }
    if (fileState != null) {
      fileState.loadState(configuration);
    } else {
      Device device = manager.getDefaultDevice();
      if (device != null) {
        configuration.myDevice = device;
        configuration.myState = device.getDefaultState();
      }
    }
    configuration.finishBulkEditing();

    assert configuration.ensureValid();
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
    copy.myTarget = original.getTarget();
    copy.myTheme = original.getTheme();
    copy.myDevice = original.getDevice();
    copy.myState = original.getDeviceState();
    copy.myActivity = original.getActivity();
    copy.myLocale = original.getLocale();
    copy.myUiMode = original.getUiMode();
    copy.myNightMode = original.getNightMode();
    copy.myDisplayName = original.getDisplayName();
    copy.myFrameworkResources = original.myFrameworkResources;
    copy.myResourceResolver = original.myResourceResolver;
    copy.myConfiguredProjectRes = original.myConfiguredProjectRes;
    copy.myConfiguredFrameworkRes = original.myConfiguredFrameworkRes;

    assert copy.ensureValid();
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
    assert source.myFile != destination.myFile; // This method is intended to sync configurations for resource variations

    FolderConfiguration editedConfig = destination.getEditedConfig();

    if (editedConfig.getVersionQualifier() == null) {
      destination.myTarget = source.getTarget();
    }
    if (editedConfig.getScreenSizeQualifier() == null) {
      destination.myDevice = source.getDevice();
    }
    if (editedConfig.getScreenOrientationQualifier() == null && editedConfig.getSmallestScreenWidthQualifier() == null) {
      destination.myState = source.getDeviceState();
    }
    if (editedConfig.getLanguageQualifier() == null) {
      destination.myLocale = source.getLocale();
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
    destination.myFrameworkResources = null;
    destination.myResourceResolver = null;
    destination.myConfiguredProjectRes = null;
    destination.myConfiguredFrameworkRes = null;

    assert destination.ensureValid();

    ProjectResources resources = ProjectResources.get(source.myManager.getModule(), true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(destination, resources, destination.myFile);
    //if (!matcher.isCurrentFileBestMatchFor(editedConfig)) {
      matcher.adaptConfigSelection(true /*needBestMatch*/);
    //}

    return destination;
  }


  public void save() {
    ConfigurationStateManager stateManager = ConfigurationStateManager.get(myManager.getModule().getProject());
    ConfigurationProjectState projectState = stateManager.getProjectState();
    projectState.saveState(this);

    if (myFile != null) {
      ConfigurationFileState fileState = new ConfigurationFileState();
      fileState.saveState(this);
      stateManager.setConfigurationState(myFile, fileState);
    }
  }

  @SuppressWarnings("AssertWithSideEffects")
  protected boolean ensureValid() {
    // Asserting on getters rather than fields since some are initialized lazily
    assert getTheme() != null;
    assert getUiMode() != null;
    assert getNightMode() != null;
    assert getLocale() != null;
    // Not checking device, state and target since this causes problem if you open
    // projects without a proper SDK configured
    return true;
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
          PsiFile file = PsiManager.getInstance(myManager.getProject()).findFile(myFile);
          if (file instanceof XmlFile) {
            XmlFile xmlFile = (XmlFile)file;
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
      myDevice = myManager.getDefaultDevice();
    }

    return myDevice;
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
      if (device != null) {
        myState = device.getDefaultState();
      }
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
   * Returns the current theme style
   *
   * @return the theme style
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
      myTarget = myManager.getDefaultTarget();
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
   * Returns whether the configuration's theme is a project theme.
   * <p/>
   * The returned value is meaningless if {@link #getTheme()} returns
   * <code>null</code>.
   *
   * @return true for project a theme, false for a framework theme
   */
  public boolean isProjectTheme() {
    String theme = getTheme();
    if (theme != null) {
      assert theme.startsWith(STYLE_RESOURCE_PREFIX) || theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX);

      return ResourceHelper.isProjectStyle(theme);
    }

    return false;
  }

  /**
   * Returns true if the current layout is locale-specific
   *
   * @return if this configuration represents a locale-specific layout
   */
  public boolean isLocaleSpecificLayout() {
    return myEditedConfig.getLanguageQualifier() != null;
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
   * Returns the full, complete {@link com.android.ide.common.resources.configuration.FolderConfiguration}
   *
   * @return the full configuration
   */
  @NotNull
  public FolderConfiguration getFullConfig() {
    if ((myFolderConfigDirty & MASK_FOLDERCONFIG) != 0) {
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
    if (myDevice != device) {
      Device prevDevice = myDevice;
      State prevState = myState;

      myDevice = device;

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
        }
        if (state == null) {
          state = device.getDefaultState();
        }
        if (myState != state) {
          myState = state;
          updateFlags |= CFG_DEVICE_STATE;
        }
      }

      // TODO: Is this redundant with the stuff above?
      if (myDevice != null && myState == null) {
        myState = myDevice.getDefaultState();
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
      myState = state;

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
    FolderConfiguration config = DeviceConfigHelper.getFolderConfig(getDeviceState());

    // replace the config with the one from the device
    myFullConfig.set(config);

    // sync the selected locale
    Locale locale = getLocale();
    myFullConfig.setLanguageQualifier(locale.language);
    myFullConfig.setRegionQualifier(locale.region);

    // Replace the UiMode with the selected one, if one is selected
    UiMode uiMode = getUiMode();
    myFullConfig.setUiModeQualifier(new UiModeQualifier(uiMode));

    // Replace the NightMode with the selected one, if one is selected
    NightMode nightMode = getNightMode();
    myFullConfig.setNightModeQualifier(new NightModeQualifier(nightMode));

    // replace the API level by the selection of the combo
    IAndroidTarget target = getTarget();
    if (target != null) {
      int apiLevel = target.getVersion().getApiLevel();
      myFullConfig.setVersionQualifier(new VersionQualifier(apiLevel));
    }

    myFolderConfigDirty = 0;
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

      // TODO: When we get a local project repository, handle this:
      //ResourceRepository frameworkRes = mConfigChooser.getClient().getFrameworkResources();
      //if (frameworkRes != null && frameworkRes.hasResourceItem(ANDROID_STYLE_RESOURCE_PREFIX + myTheme)) {
      //  myTheme = ANDROID_STYLE_RESOURCE_PREFIX + myTheme;
      //}
      //else {
      myTheme = STYLE_RESOURCE_PREFIX + myTheme;
      //}
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
      if (d != Density.NODPI) {
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
    String name = from.getName();
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i).getName().equals(name)) {
        return states.get((i + 1) % states.size());
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
  public boolean supports(Capability capability) {
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

    if ((flags & MASK_RESOLVE_RESOURCES) != 0) {
      myFrameworkResources = null;
      myConfiguredFrameworkRes = null;
      myConfiguredProjectRes = null;
      myResourceResolver = null;
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

  private ResourceResolver myResourceResolver;
  private FrameworkResources myFrameworkResources;
  private Map<ResourceType, Map<String, ResourceValue>> myConfiguredFrameworkRes;
  private Map<ResourceType, Map<String, ResourceValue>> myConfiguredProjectRes;
  private long myCachedGeneration;

  @Nullable
  public ResourceResolver getResourceResolver() {
    ProjectResources resources = ProjectResources.get(myManager.getModule(), true);
    if (myCachedGeneration < resources.getModificationCount()) {
      myResourceResolver = null;
    }

    if (myResourceResolver == null) {
      String themeStyle = getTheme();
      if (themeStyle == null) {
        LOG.error("Missing theme.");
        return null;
      }
      boolean isProjectTheme = isProjectTheme();
      String theme = ResourceHelper.styleToTheme(themeStyle);

      Map<ResourceType, Map<String, ResourceValue>> configuredProjectRes = getConfiguredProjectResources();

      // Get the framework resources
      Map<ResourceType, Map<String, ResourceValue>> frameworkResources = getConfiguredFrameworkResources();
      myResourceResolver = ResourceResolver.create(configuredProjectRes, frameworkResources, theme, isProjectTheme);
    }

    return myResourceResolver;
  }

  @NotNull
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredFrameworkResources() {
    if (myConfiguredFrameworkRes == null) {
      ResourceRepository frameworkRes = getFrameworkResources();

      if (frameworkRes == null) {
        myConfiguredFrameworkRes = Collections.emptyMap();
      }
      else {
        // get the framework resource values based on the current config
        myConfiguredFrameworkRes = frameworkRes.getConfiguredResources(getFullConfig());
      }
    }

    return myConfiguredFrameworkRes;
  }

  /**
   * Returns a {@link ProjectResources} for the framework resources based on the current
   * configuration selection.
   *
   * @return the framework resources or null if not found.
   */
  @Nullable
  public ResourceRepository getFrameworkResources() {
    // TODO: This should be cached elsewhere!
    if (myFrameworkResources == null) {
      IAndroidTarget target = getTarget();
      if (target != null) {
        myFrameworkResources = getFrameworkResources(target, getConfigurationManager().getModule());
      }
    }

    return myFrameworkResources;
  }

  /**
   * Returns a {@link ProjectResources} for the framework resources of a given
   * target.
   *
   * @param target the target for which to return the framework resources.
   * @return the framework resources or null if not found.
   */
  @Nullable
  private static FrameworkResources getFrameworkResources(@NotNull IAndroidTarget target, @NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      return null;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      return null;
    }

    AndroidTargetData targetData = platform.getSdkData().getTargetData(target);
    try {
      Project project = module.getProject();
      RenderServiceFactory factory = targetData.getRenderServiceFactory(project);
      if (factory != null) {
        return factory.getFrameworkResources();
      }
    }
    catch (RenderingException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  @NotNull
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredProjectResources() {
    final ProjectResources resources = ProjectResources.get(myManager.getModule(), true);
    if (myConfiguredProjectRes == null || myCachedGeneration < resources.getModificationCount()) {
      // get the project resource values based on the current config
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          myConfiguredProjectRes = resources.getConfiguredResources(getFullConfig());
        }
      });
      myCachedGeneration = resources.getModificationCount();
    }

    return myConfiguredProjectRes;
  }

  // For debugging only
  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public String toString() {
    return Objects.toStringHelper(this.getClass()).add("display", getDisplayName())      //$NON-NLS-1$
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
    ProjectResources resources = ProjectResources.get(getModule(), true);
    return new ConfigurationMatcher(this, resources, file).isCurrentFileBestMatchFor(config);
  }

  @Override
  public void dispose() {
  }
}
