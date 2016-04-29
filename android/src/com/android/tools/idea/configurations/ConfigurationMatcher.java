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

import com.android.ide.common.resources.configuration.*;
import com.android.io.IAbstractFile;
import com.android.resources.*;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.SparseIntArray;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.uipreview.VirtualFileWrapper;
import org.jetbrains.android.util.BufferingFileWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FD_RES_LAYOUT;

/**
 * Produces matches for configurations.
 * <p/>
 * See algorithm described here:
 * http://developer.android.com/guide/topics/resources/providing-resources.html#BestMatch
 * <p>
 * This class was ported from ADT and could probably use a rewrite.
 */
public class ConfigurationMatcher {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ConfigurationMatcher");

  private final Configuration myConfiguration;
  private final ConfigurationManager myManager;
  private final VirtualFile myFile;
  private final LocalResourceRepository myResources;

  public ConfigurationMatcher(@NotNull Configuration configuration,
                       @Nullable LocalResourceRepository resources,
                       @Nullable VirtualFile file) {
    myConfiguration = configuration;
    myFile = file;
    myResources = resources;

    myManager = myConfiguration.getConfigurationManager();
  }

  // ---- Finding matching configurations ----

  private static class ConfigBundle {
    private final FolderConfiguration config;
    private int localeIndex;
    private int dockModeIndex;
    private int nightModeIndex;

    private ConfigBundle() {
      config = new FolderConfiguration();
    }

    private ConfigBundle(ConfigBundle bundle) {
      config = new FolderConfiguration();
      config.set(bundle.config);
      localeIndex = bundle.localeIndex;
      dockModeIndex = bundle.dockModeIndex;
      nightModeIndex = bundle.nightModeIndex;
    }

    @Override
    public String toString() {
      return config.getUniqueKey();
    }
  }

  private static class ConfigMatch {
    final FolderConfiguration testConfig;
    final Device device;
    final State state;
    final ConfigBundle bundle;

    public ConfigMatch(@NotNull FolderConfiguration testConfig,
                       @NotNull Device device,
                       @NotNull State state,
                       @NotNull ConfigBundle bundle) {
      this.testConfig = testConfig;
      this.device = device;
      this.state = state;
      this.bundle = bundle;
    }

    @Override
    public String toString() {
      return device.getDisplayName() + " - " + state.getName() + " - " + bundle;
    }
  }

  /**
  * Checks whether the current edited file is the best match for a given config.
  * <p/>
  * This tests against other versions of the same layout in the project.
  * <p/>
  * The given config must be compatible with the current edited file.
  *
  * @param config the config to test.
  * @return true if the current edited file is the best match in the project for the
  *         given config.
  */
  public boolean isCurrentFileBestMatchFor(@NotNull FolderConfiguration config) {
    if (myResources != null && myFile != null) {
      VirtualFile match = myResources.getMatchingFile(myFile, getResourceType(), config);
      if (match != null) {
        return myFile.equals(match);
      }
      else {
        // if we stop here that means the current file is not even a match!
        LOG.info("Current file is not a match for the given config.");
      }
    }

    return false;
  }

  private ResourceType getResourceType() {
    // We're usually using the ConfigurationMatcher for layouts, but support other types too
    ResourceType type = ResourceType.LAYOUT;
    VirtualFile parent = myFile.getParent();
    if (parent != null) {
      String parentName = parent.getName();
      if (!parentName.startsWith(FD_RES_LAYOUT)) {
        ResourceFolderType folderType = ResourceHelper.getFolderType(myFile);
        if (folderType != null) {
          List<ResourceType> related = FolderTypeRelationship.getRelatedResourceTypes(folderType);
          if (!related.isEmpty()) {
            type = related.get(0); // the primary type is always first
          }
        }
      }
    }
    return type;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull IAbstractFile file) {
    if (file instanceof VirtualFileWrapper) {
      return ((VirtualFileWrapper)file).getFile();
    } else if (file instanceof BufferingFileWrapper) {
      BufferingFileWrapper wrapper = (BufferingFileWrapper)file;
      File ioFile = wrapper.getFile();
      return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
    } else {
      LOG.warn("Unexpected type of match file: " + file.getClass().getName());
    }
    return null;
  }

  /**
   * Returns a list of {@link VirtualFile} which best match the configuration.
   */
  @NotNull
  public List<VirtualFile> getBestFileMatches() {
    if (myResources != null && myFile != null) {
      FolderConfiguration config = myConfiguration.getFullConfig();
      VersionQualifier prevQualifier = config.getVersionQualifier();
      try {
        config.setVersionQualifier(null);
        return myResources.getMatchingFiles(myFile, getResourceType(), config);
      }
      finally {
        config.setVersionQualifier(prevQualifier);
      }
    }
    return Collections.emptyList();
  }

  /** Like {@link ConfigurationManager#getLocales()}, but ensures that the currently selected locale is first in the list */
  @NotNull
  public List<Locale> getPrioritizedLocales() {
    List<Locale> projectLocales = myManager.getLocales();
    List<Locale> locales = new ArrayList<Locale>(projectLocales.size() + 1); // Locale.ANY is not in getLocales() list
    Locale current = myManager.getLocale();
    locales.add(current);
    for (Locale locale : projectLocales) {
      if (!locale.equals(current)) {
        locales.add(locale);
      }
    }

    return locales;
  }

  /**
   * Adapts the current device/config selection so that it's compatible with
   * the configuration.
   * <p/>
   * If the current selection is compatible, nothing is changed.
   * <p/>
   * If it's not compatible, configs from the current devices are tested.
   * <p/>
   * If none are compatible, it reverts to
   * {@link #findAndSetCompatibleConfig(boolean)}
   */
  void adaptConfigSelection(boolean needBestMatch) {
    // check the device config (ie sans locale)
    boolean needConfigChange = true; // if still true, we need to find another config.
    boolean currentConfigIsCompatible = false;
    State selectedState = myConfiguration.getDeviceState();
    FolderConfiguration editedConfig = myConfiguration.getEditedConfig();
    Module module = myConfiguration.getModule();
    if (selectedState != null) {
      FolderConfiguration currentConfig = Configuration.getFolderConfig(module, selectedState, myConfiguration.getLocale(),
                                                                        myConfiguration.getTarget());
      if (currentConfig != null && editedConfig.isMatchFor(currentConfig)) {
        currentConfigIsCompatible = true; // current config is compatible
        if (!needBestMatch || isCurrentFileBestMatchFor(currentConfig)) {
          needConfigChange = false;
        }
      }
    }

    if (needConfigChange) {
      List<Locale> localeList = getPrioritizedLocales();

      // if the current state/locale isn't a correct match, then
      // look for another state/locale in the same device.
      FolderConfiguration testConfig = new FolderConfiguration();

      // first look in the current device.
      State matchState = null;
      int localeIndex = -1;
      Device device = myConfiguration.getDevice();
      IAndroidTarget target = myConfiguration.getTarget();
      if (device != null && target != null) {
        VersionQualifier versionQualifier = new VersionQualifier(target.getVersion().getFeatureLevel());
        mainloop:
        for (State state : device.getAllStates()) {
          testConfig.set(Configuration.getFolderConfig(module, state, myConfiguration.getLocale(), target));
          testConfig.setVersionQualifier(versionQualifier);

          // loop on the locales.
          for (int i = 0; i < localeList.size(); i++) {
            Locale locale = localeList.get(i);

            // update the test config with the locale qualifiers
            testConfig.setLocaleQualifier(locale.qualifier);

            if (editedConfig.isMatchFor(testConfig) && isCurrentFileBestMatchFor(testConfig)) {
              matchState = state;
              localeIndex = i;
              break mainloop;
            }
          }
        }
      }

      if (matchState != null) {
        myConfiguration.startBulkEditing();
        myConfiguration.setDeviceState(matchState);
        myConfiguration.setEffectiveDevice(device, matchState);
        myConfiguration.finishBulkEditing();
      }
      else {
        // no match in current device with any state/locale
        // attempt to find another device that can display this
        // particular state.
        findAndSetCompatibleConfig(currentConfigIsCompatible);
      }
    }
  }

  /**
   * Finds a device/config that can display a configuration.
   * <p/>
   * Once found the device and config combos are set to the config.
   * <p/>
   * If there is no compatible configuration, a custom one is created.
   *
   * @param favorCurrentConfig if true, and no best match is found, don't
   *                           change the current config. This must only be true if the
   *                           current config is compatible.
   */
  void findAndSetCompatibleConfig(boolean favorCurrentConfig) {
    List<Locale> localeList = getPrioritizedLocales();
    List<Device> deviceList = myManager.getDevices();
    FolderConfiguration editedConfig = myConfiguration.getEditedConfig();
    FolderConfiguration currentConfig = myConfiguration.getFullConfig();

    // list of compatible device/state/locale
    List<ConfigMatch> anyMatches = new ArrayList<ConfigMatch>();

    // list of actual best match (ie the file is a best match for the
    // device/state)
    List<ConfigMatch> bestMatches = new ArrayList<ConfigMatch>();

    // get a locale that matches the host locale roughly (may not be exact match on the region.)
    int localeHostMatch = getLocaleMatch();

    // build a list of combinations of non standard qualifiers to add to each device's
    // qualifier set when testing for a match.
    // These qualifiers are: locale, night-mode, car dock.
    List<ConfigBundle> configBundles = new ArrayList<ConfigBundle>(200);

    // If the edited file has locales, then we have to select a matching locale from
    // the list.
    // However, if it doesn't, we don't randomly take the first locale, we take one
    // matching the current host locale (making sure it actually exist in the project)
    int start, max;
    if (editedConfig.getLocaleQualifier() != null || localeHostMatch == -1) {
      // add all the locales
      start = 0;
      max = localeList.size();
    }
    else {
      // only add the locale host match
      start = localeHostMatch;
      max = localeHostMatch + 1; // test is <
    }

    for (int i = start; i < max; i++) {
      Locale l = localeList.get(i);

      ConfigBundle bundle = new ConfigBundle();
      bundle.config.setLocaleQualifier(l.qualifier);
      bundle.localeIndex = i;
      configBundles.add(bundle);
    }

    // add the dock mode to the bundle combinations.
    addDockModeToBundles(configBundles);

    // add the night mode to the bundle combinations.
    addNightModeToBundles(configBundles);

    addRenderTargetToBundles(configBundles);

    Locale currentLocale = myConfiguration.getLocale();
    IAndroidTarget currentTarget = myConfiguration.getTarget();
    Module module = myConfiguration.getModule();

    for (Device device : deviceList) {
      for (State state : device.getAllStates()) {

        // loop on the list of config bundles to create full
        // configurations.
        FolderConfiguration stateConfig = Configuration.getFolderConfig(module, state, currentLocale, currentTarget);
        for (ConfigBundle bundle : configBundles) {
          // create a new config with device config
          FolderConfiguration testConfig = new FolderConfiguration();
          testConfig.set(stateConfig);

          // add on top of it, the extra qualifiers from the bundle
          testConfig.add(bundle.config);

          if (editedConfig.isMatchFor(testConfig)) {
            // this is a basic match. record it in case we don't
            // find a match
            // where the edited file is a best config.
            anyMatches.add(new ConfigMatch(testConfig, device, state, bundle));

            if (isCurrentFileBestMatchFor(testConfig)) {
              // this is what we want.
              bestMatches.add(new ConfigMatch(testConfig, device, state, bundle));
            }
          }
        }
      }
    }

    if (bestMatches.size() == 0) {
      if (favorCurrentConfig) {
        // quick check
        if (!editedConfig.isMatchFor(currentConfig)) {
          LOG.warn("favorCurrentConfig can only be true if the current config is compatible");
        }

        // just display the warning
        LOG.warn(String.format("'%1$s' is not a best match for any device/locale combination for %2$s.\n" +
                               "Displaying it with '%3$s'.",
                               editedConfig.toDisplayString(), myConfiguration.getFile(), currentConfig.toDisplayString()));
      }
      else if (anyMatches.size() > 0) {
        // select the best device anyway.
        ConfigMatch match = selectConfigMatch(anyMatches);

        myConfiguration.startBulkEditing();
        myConfiguration.setEffectiveDevice(match.device, match.state);
        myConfiguration.setUiMode(UiMode.getByIndex(match.bundle.dockModeIndex));
        myConfiguration.setNightMode(NightMode.getByIndex(match.bundle.nightModeIndex));
        myConfiguration.finishBulkEditing();

        // TODO: display a better warning!
        LOG.warn(String.format("'%1$s' is not a best match for any device/locale combination for %2$s.\n" +
                               "Displaying it with\n" +
                               "  %3$s\n" +
                               "which is compatible, but will actually be displayed with " +
                               "another more specific version of the layout.", editedConfig.toDisplayString(),
                               myConfiguration.getFile(), currentConfig.toDisplayString()));
      }
      else {
        // TODO: there is no device/config able to display the layout, create one.
        // For the base config values, we'll take the first device and state,
        // and replace whatever qualifier required by the layout file.
      }
    }
    else {
      ConfigMatch match = selectConfigMatch(bestMatches);

      myConfiguration.startBulkEditing();
      myConfiguration.setEffectiveDevice(match.device, match.state);
      myConfiguration.setUiMode(UiMode.getByIndex(match.bundle.dockModeIndex));
      myConfiguration.setNightMode(NightMode.getByIndex(match.bundle.nightModeIndex));
      myConfiguration.finishBulkEditing();
    }
  }

  private void addRenderTargetToBundles(List<ConfigBundle> configBundles) {
    IAndroidTarget target = myManager.getTarget();
    if (target != null) {
      int apiLevel = target.getVersion().getFeatureLevel();
      for (ConfigBundle bundle : configBundles) {
        bundle.config.setVersionQualifier(new VersionQualifier(apiLevel));
      }
    }
  }

  private static void addDockModeToBundles(List<ConfigBundle> addConfig) {
    ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

    // loop on each item and for each, add all variations of the dock modes
    for (ConfigBundle bundle : addConfig) {
      int index = 0;
      for (UiMode mode : UiMode.values()) {
        ConfigBundle b = new ConfigBundle(bundle);
        b.config.setUiModeQualifier(new UiModeQualifier(mode));
        b.dockModeIndex = index++;
        list.add(b);
      }
    }

    addConfig.clear();
    addConfig.addAll(list);
  }

  private static void addNightModeToBundles(List<ConfigBundle> addConfig) {
    ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

    // loop on each item and for each, add all variations of the night modes
    for (ConfigBundle bundle : addConfig) {
      int index = 0;
      for (NightMode mode : NightMode.values()) {
        ConfigBundle b = new ConfigBundle(bundle);
        b.config.setNightModeQualifier(new NightModeQualifier(mode));
        b.nightModeIndex = index++;
        list.add(b);
      }
    }

    addConfig.clear();
    addConfig.addAll(list);
  }

  private int getLocaleMatch() {
    java.util.Locale defaultLocale = java.util.Locale.getDefault();
    if (defaultLocale != null) {
      String currentLanguage = defaultLocale.getLanguage();
      String currentRegion = defaultLocale.getCountry();

      List<Locale> localeList = myManager.getLocales();
      final int count = localeList.size();
      for (int l = 0; l < count; l++) {
        Locale locale = localeList.get(l);
        LocaleQualifier qualifier = locale.qualifier;

        // there's always a ##/Other or ##/Any (which is the same, the region
        // contains FAKE_REGION_VALUE). If we don't find a perfect region match
        // we take the fake region. Since it's last in the list, this makes the
        // test easy.
        if (qualifier.getLanguage().equals(currentLanguage) &&
            (qualifier.getRegion() == null || qualifier.getRegion().equals(currentRegion))) {
          return l;
        }
      }

      // If no exact region match, try to just match on the language
      for (int l = 0; l < count; l++) {
        Locale locale = localeList.get(l);
        LocaleQualifier qualifier = locale.qualifier;

        // there's always a ##/Other or ##/Any (which is the same, the region
        // contains FAKE_REGION_VALUE). If we don't find a perfect region match
        // we take the fake region. Since it's last in the list, this makes the
        // test easy.
        if (qualifier.getLanguage().equals(currentLanguage)) {
          return l;
        }
      }
    }

    // Nothing found: use the first one (which should be the current locale); see
    // getPrioritizedLocales()
    return 0;
  }

  @NotNull
  private ConfigMatch selectConfigMatch(@NotNull List<ConfigMatch> matches) {
    List<String> deviceIds = myManager.getStateManager().getProjectState().getDeviceIds();
    Map<String, Integer> idRank = Maps.newHashMapWithExpectedSize(deviceIds.size());
    int rank = 0;
    for (String id : deviceIds) {
      idRank.put(id, rank++);
    }

    // API 11-13: look for a x-large device
    Comparator<ConfigMatch> comparator = null;
    IAndroidTarget projectTarget = myManager.getProjectTarget();
    if (projectTarget != null) {
      int apiLevel = projectTarget.getVersion().getFeatureLevel();
      if (apiLevel >= 11 && apiLevel < 14) {
        // TODO: Maybe check the compatible-screen tag in the manifest to figure out
        // what kind of device should be used for display.
        comparator = new TabletConfigComparator(idRank);
      }
    }

    if (comparator == null) {
      // lets look for a high density device
      comparator = new PhoneConfigComparator(idRank);
    }

    Collections.sort(matches, comparator);

    // Look at the currently active editor to see if it's a layout editor, and if so,
    // look up its configuration and if the configuration is in our match list,
    // use it. This means we "preserve" the current configuration when you open
    // new layouts.
    // TODO: This is running too late for the layout preview; the new editor has
    // already taken over so getSelectedTextEditor() returns self. Perhaps we
    // need to fish in the open editors instead.

    // We use FileEditorManagerImpl instead of FileEditorManager to get access to the lock-free version
    // (also used by DebuggerContextUtil) since the normal method only works from the dispatch thread
    // (grabbing a read lock is not enough).
    Editor activeEditor = ((FileEditorManagerImpl)FileEditorManager.getInstance(myManager.getProject())).getSelectedTextEditor(true);
    if (activeEditor != null) {
      FileDocumentManager documentManager = FileDocumentManager.getInstance();
      VirtualFile file = documentManager.getFile(activeEditor.getDocument());
      if (file != null && !file.equals(myFile) && file.getFileType() == StdFileTypes.XML
          && ResourceHelper.getFolderType(myFile) == ResourceHelper.getFolderType(file)) {
        Configuration configuration = myManager.getConfiguration(file);
        FolderConfiguration fullConfig = configuration.getFullConfig();
        for (ConfigMatch match : matches) {
          if (fullConfig.equals(match.testConfig)) {
            return match;
          }
        }
      }
    }

    // the list has been sorted so that the first item is the best config
    return matches.get(0);
  }

  /**
   * Returns a different file which is a better match for the given device, orientation, target version, etc
   * than the current one. You can supply {@code null} for all parameters; in that case, the current value
   * in the configuration is used.
   */
  @Nullable
  public static VirtualFile getBetterMatch(@NotNull Configuration configuration, @Nullable Device device, @Nullable String stateName,
                                           @Nullable Locale locale, @Nullable IAndroidTarget target) {
    VirtualFile file = configuration.getFile();
    Module module = configuration.getModule();
    if (file != null && module != null) {
      if (device == null) {
        device = configuration.getDevice();
      }
      if (stateName == null) {
        State deviceState = configuration.getDeviceState();
        stateName = deviceState != null ? deviceState.getName() : null;
      }
      State selectedState = ConfigurationFileState.getState(device, stateName);
      if (selectedState == null) {
        return null; // Invalid state name passed in for the current device
      }
      if (locale == null) {
        locale = configuration.getLocale();
      }
      if (target == null) {
        target = configuration.getTarget();
      }
      FolderConfiguration currentConfig = Configuration.getFolderConfig(module, selectedState, locale, target);
      if (currentConfig != null) {
        LocalResourceRepository resources = AppResourceRepository.getAppResources(module, true);
        if (resources != null) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(file);
          if (folderType != null) {
            List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
            if (!types.isEmpty()) {
              ResourceType type = types.get(0);
              List<VirtualFile> matches = resources.getMatchingFiles(file, type, currentConfig);
              if (!matches.contains(file) && !matches.isEmpty()) {
                return matches.get(0);
              }
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * Note: this comparator imposes orderings that are inconsistent with equals.
   */
  private static class TabletConfigComparator implements Comparator<ConfigMatch> {
    private final Map<String, Integer> mIdRank;
    private static final String PREFERRED_ID = "Nexus 10";

    private TabletConfigComparator(Map<String, Integer> idRank) {
      mIdRank = idRank;
    }

    @Override
    public int compare(ConfigMatch o1, ConfigMatch o2) {
      FolderConfiguration config1 = o1 != null ? o1.testConfig : null;
      FolderConfiguration config2 = o2 != null ? o2.testConfig : null;
      if (config1 == null) {
        if (config2 == null) {
          return 0;
        }
        else {
          return -1;
        }
      }
      else if (config2 == null) {
        return 1;
      }

      String n1 = o1.device.getId();
      String n2 = o2.device.getId();

      Integer rank1 = mIdRank.get(o1.device.getId());
      Integer rank2 = mIdRank.get(o2.device.getId());
      if (rank1 != null) {
        if (rank2 != null) {
          int delta = rank1 - rank2;
          if (delta != 0) {
            return delta;
          }
        } else {
          return -1;
        }
      } else if (rank2 != null) {
        return 1;
      }

      // Default to a modern device
      if (n1.equals(PREFERRED_ID)) {
        return n2.equals(PREFERRED_ID) ? 0 : -1;
      } else if (n2.equals(PREFERRED_ID)) {
        return 1;
      }

      ScreenSizeQualifier size1 = config1.getScreenSizeQualifier();
      ScreenSizeQualifier size2 = config2.getScreenSizeQualifier();
      ScreenSize ss1 = size1 != null ? size1.getValue() : ScreenSize.NORMAL;
      ScreenSize ss2 = size2 != null ? size2.getValue() : ScreenSize.NORMAL;

      // X-LARGE is better than all others (which are considered identical)
      // if both X-LARGE, then LANDSCAPE is better than all others (which are identical)

      if (ss1 == ScreenSize.XLARGE) {
        if (ss2 == ScreenSize.XLARGE) {
          ScreenOrientationQualifier orientation1 = config1.getScreenOrientationQualifier();
          ScreenOrientation so1 = orientation1.getValue();
          if (so1 == null) {
            so1 = ScreenOrientation.PORTRAIT;
          }
          ScreenOrientationQualifier orientation2 = config2.getScreenOrientationQualifier();
          ScreenOrientation so2 = orientation2.getValue();
          if (so2 == null) {
            so2 = ScreenOrientation.PORTRAIT;
          }

          if (so1 == ScreenOrientation.LANDSCAPE) {
            if (so2 == ScreenOrientation.LANDSCAPE) {
              return 0;
            }
            else {
              return -1;
            }
          }
          else if (so2 == ScreenOrientation.LANDSCAPE) {
            return 1;
          }
          else {
            return 0;
          }
        }
        else {
          return -1;
        }
      }
      else if (ss2 == ScreenSize.XLARGE) {
        return 1;
      }
      else {
        return 0;
      }
    }
  }

  /**
   * Note: this comparator imposes orderings that are inconsistent with equals.
   */
  private static class PhoneConfigComparator implements Comparator<ConfigMatch> {
    // Default phone. Not picking the Nexus 5 yet since it has much higher resolution
    // (which will on a typical desktop rendering of the layout be scaled back down again anyway)
    // so it's just extra work.
    private static final String PREFERRED_ID = "Nexus 4";

    private final SparseIntArray mDensitySort = new SparseIntArray(4);
    private final Map<String, Integer> mIdRank;

    public PhoneConfigComparator(Map<String, Integer> idRank) {
      int i = 0;
      mDensitySort.put(Density.HIGH.getDpiValue(), ++i);
      mDensitySort.put(Density.MEDIUM.getDpiValue(), ++i);
      mDensitySort.put(Density.XHIGH.getDpiValue(), ++i);
      mDensitySort.put(Density.DPI_400.getDpiValue(), ++i);
      mDensitySort.put(Density.XXHIGH.getDpiValue(), ++i);
      mDensitySort.put(Density.DPI_560.getDpiValue(), ++i);
      mDensitySort.put(Density.XXXHIGH.getDpiValue(), ++i);
      mDensitySort.put(Density.DPI_420.getDpiValue(), ++i);
      mDensitySort.put(Density.DPI_360.getDpiValue(), ++i);
      mDensitySort.put(Density.DPI_280.getDpiValue(), ++i);
      mDensitySort.put(Density.TV.getDpiValue(), ++i);
      mDensitySort.put(Density.LOW.getDpiValue(), ++i);

      mIdRank = idRank;
    }

    @Override
    public int compare(ConfigMatch o1, ConfigMatch o2) {
      FolderConfiguration config1 = o1 != null ? o1.testConfig : null;
      FolderConfiguration config2 = o2 != null ? o2.testConfig : null;
      if (config1 == null) {
        if (config2 == null) {
          return 0;
        }
        else {
          return -1;
        }
      }
      else if (config2 == null) {
        return 1;
      }

      String n1 = o1.device.getId();
      String n2 = o2.device.getId();

      Integer rank1 = mIdRank.get(o1.device.getId());
      Integer rank2 = mIdRank.get(o2.device.getId());
      if (rank1 != null) {
        if (rank2 != null) {
          int delta = rank1 - rank2;
          if (delta != 0) {
            return delta;
          }
        } else {
          return -1;
        }
      } else if (rank2 != null) {
        return 1;
      }

      // Default to a modern device
      if (n1.equals(PREFERRED_ID)) {
        return n2.equals(PREFERRED_ID) ? 0 : -1;
      } else if (n2.equals(PREFERRED_ID)) {
        return 1;
      }

      int dpi1 = Density.DEFAULT_DENSITY;
      int dpi2 = Density.DEFAULT_DENSITY;

      DensityQualifier dpiQualifier1 = config1.getDensityQualifier();
      if (dpiQualifier1 != null) {
        Density value = dpiQualifier1.getValue();
        dpi1 = value != null ? value.getDpiValue() : Density.DEFAULT_DENSITY;
      }
      dpi1 = mDensitySort.get(dpi1, 100 /* valueIfKeyNotFound*/);

      DensityQualifier dpiQualifier2 = config2.getDensityQualifier();
      if (dpiQualifier2 != null) {
        Density value = dpiQualifier2.getValue();
        dpi2 = value != null ? value.getDpiValue() : Density.DEFAULT_DENSITY;
      }
      dpi2 = mDensitySort.get(dpi2, 100 /* valueIfKeyNotFound*/);

      if (dpi1 == dpi2) {
        // portrait is better
        ScreenOrientation so1 = ScreenOrientation.PORTRAIT;
        ScreenOrientationQualifier orientationQualifier1 = config1.getScreenOrientationQualifier();
        if (orientationQualifier1 != null) {
          so1 = orientationQualifier1.getValue();
          if (so1 == null) {
            so1 = ScreenOrientation.PORTRAIT;
          }
        }
        ScreenOrientation so2 = ScreenOrientation.PORTRAIT;
        ScreenOrientationQualifier orientationQualifier2 = config2.getScreenOrientationQualifier();
        if (orientationQualifier2 != null) {
          so2 = orientationQualifier2.getValue();
          if (so2 == null) {
            so2 = ScreenOrientation.PORTRAIT;
          }
        }

        if (so1 == ScreenOrientation.PORTRAIT) {
          if (so2 == ScreenOrientation.PORTRAIT) {
            return 0;
          }
          else {
            return -1;
          }
        }
        else if (so2 == ScreenOrientation.PORTRAIT) {
          return 1;
        }
        else {
          return 0;
        }
      }

      return dpi1 - dpi2;
    }
  }
}
