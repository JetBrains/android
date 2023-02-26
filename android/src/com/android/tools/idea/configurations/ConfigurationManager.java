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

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_LOCALE;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_TARGET;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import com.android.tools.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import com.android.tools.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A {@linkplain ConfigurationManager} is responsible for managing {@link Configuration}
 * objects for a given project.
 * <p>
 * Whereas a {@link Configuration} is tied to a specific render target or theme,
 * the {@linkplain ConfigurationManager} knows the set of available targets, themes,
 * locales etc. for the current project.
 * <p>
 * The {@linkplain ConfigurationManager} is also responsible for storing and retrieving
 * the saved configuration state for a given file.
 */
public class ConfigurationManager implements Disposable {
  private static final Key<ConfigurationManager> KEY = Key.create(ConfigurationManager.class.getName());
  private static final Key<VirtualFile> CONFIGURATION_MANAGER_PROJECT_CANONICAL_KEY = Key.create(
    ConfigurationManager.class.getName() + "ProjectCanonicalKey"
  );

  @NotNull private final Module myModule;
  private final Map<VirtualFile, Configuration> myCache = ContainerUtil.createSoftValueMap();
  private Device myDefaultDevice;
  private Locale myLocale;
  private IAndroidTarget myTarget;
  private int myStateVersion;
  private ResourceResolverCache myResolverCache;

  @NotNull
  public static ConfigurationManager getOrCreateInstance(@NotNull Module module) {
    return findConfigurationManager(module, true /* create if necessary */);
  }

  @Nullable
  public static ConfigurationManager findExistingInstance(@NotNull Module module) {
    return findConfigurationManager(module, false /* do not create if not found */);
  }

  @Contract("_, true -> !null")
  @Nullable
  private static ConfigurationManager findConfigurationManager(@NotNull Module module, boolean createIfNecessary) {
    ConfigurationManager configurationManager = module.getUserData(KEY);
    if (configurationManager == null && createIfNecessary) {
      configurationManager = new ConfigurationManager(module);
      module.putUserData(KEY, configurationManager);
    }
    return configurationManager;
  }

  /**
   * In some tests the project might not have a project file. We use this to create a per-project canonical file that can be used to
   * associate the default project configuration to.
   */
  @NotNull
  private static VirtualFile getFakeProjectFile(@NotNull Project project) {
    VirtualFile projectFile = CONFIGURATION_MANAGER_PROJECT_CANONICAL_KEY.get(project);
    if (projectFile == null) {
      VirtualFile parent = new LightVirtualFile("layout");
      projectFile = new LightVirtualFile("no-project-file") {
        @Override
        public VirtualFile getParent() {
          return parent;
        }
      };
      CONFIGURATION_MANAGER_PROJECT_CANONICAL_KEY.set(project, projectFile);
    }

    return projectFile;
  }

  /**
   * Gets the {@link Configuration} associated with the given module.
   *
   * @return the {@link Configuration} for the given module.
   */
  @Slow
  @NotNull
  public static Configuration getConfigurationForModule(@NotNull Module module) {
    Project project = module.getProject();
    ConfigurationManager configurationManager = getOrCreateInstance(module);

    VirtualFile projectFile = project.getProjectFile();
    if (projectFile == null) {
      projectFile = getFakeProjectFile(project);
    }

    return configurationManager.getConfiguration(projectFile);
  }

  protected ConfigurationManager(@NotNull Module module) {
    myModule = module;
    Disposer.register(myModule, this);
  }

  /**
   * Gets the {@link Configuration} associated with the given file
   *
   * @return the {@link Configuration} for the given file
   */
  @Slow
  @NotNull
  public Configuration getConfiguration(@NotNull VirtualFile file) {
    Configuration configuration = myCache.get(file);
    if (configuration == null) {
      configuration = create(file);
      myCache.put(file, configuration);
    }

    return configuration;
  }

  @TestOnly
  boolean hasCachedConfiguration(@NotNull VirtualFile file) {
    return myCache.get(file) != null;
  }

  /**
   * Creates and returns a new {@link Configuration} associated with this manager.
   * This method might block while finding the correct {@link Device} for the {@link Configuration}. Finding
   * devices requires accessing (and maybe updating) the repository of existing ones.
   */
  @Slow
  @NotNull
  private Configuration create(@NotNull VirtualFile file) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationFileState fileState = stateManager.getConfigurationState(file);
    assert file.getParent() != null : file;
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, file, fileState, config);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, file);
    if (fileState != null) {
      matcher.adaptConfigSelection(true);
    }
    else {
      matcher.findAndSetCompatibleConfig(false);
    }

    return configuration;
  }

  /**
   * Similar to {@link #getConfiguration(VirtualFile)}, but creates a configuration
   * for a file known to be new, and crucially, bases the configuration on the existing configuration
   * for a known file. This is intended for when you fork a layout, and you expect the forked layout
   * to have a configuration that is (as much as possible) similar to the configuration of the
   * forked file. For example, if you create a landscape version of a layout, it will preserve the
   * screen size, locale, theme and render target of the existing layout.
   *
   * @param file     the file to create a configuration for
   * @param baseFile the other file to base the configuration on
   * @return the new configuration
   */
  @NotNull
  public Configuration createSimilar(@NotNull VirtualFile file, @NotNull VirtualFile baseFile) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationFileState fileState = stateManager.getConfigurationState(baseFile);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, file, fileState, config);
    Configuration baseConfig = myCache.get(file);
    if (baseConfig != null) {
      configuration.setEffectiveDevice(baseConfig.getDevice(), baseConfig.getDeviceState());
    }
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, file);
    matcher.adaptConfigSelection(true /*needBestMatch*/);
    myCache.put(file, configuration);

    return configuration;
  }

  /**
   * Returns the associated persistence manager
   */
  public ConfigurationStateManager getStateManager() {
    return ConfigurationStateManager.get(myModule.getProject());
  }

  /**
   * Returns the list of available devices for the current platform and any custom user devices, if any
   */
  @Slow
  @NotNull
  public ImmutableList<Device> getDevices() {
    AndroidPlatform platform = AndroidPlatforms.getInstance(myModule);
    if (platform == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Device> builder = new ImmutableList.Builder<>();
    builder.addAll(platform.getSdkData().getDeviceManager().getDevices(DeviceManager.ALL_DEVICES));
    AdditionalDeviceService ads = AdditionalDeviceService.getInstance();
    if (ads != null) {
      builder.addAll(ads.getWindowSizeDevices());
    }
    return builder.build();
  }

  @Nullable
  public Device getDeviceById(@NotNull String id) {
    return getDevices()
      .stream()
      .filter(device -> device.getId().equals(id))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  public Device createDeviceForAvd(@NotNull AvdInfo avd) {
    AndroidPlatform platform = AndroidPlatforms.getInstance(myModule);
    if (platform == null) {
      return null;
    }
    Device modelDevice = platform.getSdkData().getDeviceManager().getDevice(avd.getDeviceName(), avd.getDeviceManufacturer());
    if (modelDevice == null) {
      return null;
    }
    String avdName = avd.getName();
    Device.Builder builder = new Device.Builder(modelDevice);
    builder.setName(avd.getDisplayName());
    builder.setId(Configuration.AVD_ID_PREFIX + avdName);
    return builder.build();
  }

  public static boolean isAvdDevice(@NotNull Device device) {
    return device.getId().startsWith(Configuration.AVD_ID_PREFIX);
  }

  /**
   * Returns all the {@link IAndroidTarget} instances applicable for the current module.
   * Note that this may include non-rendering targets, so for layout rendering contexts,
   * check individual members by calling {@link #isLayoutLibTarget(IAndroidTarget)} first.
   */
  @NotNull
  public IAndroidTarget[] getTargets() {
    AndroidPlatform platform = AndroidPlatforms.getInstance(myModule);
    if (platform != null) {
      final AndroidSdkData sdkData = platform.getSdkData();

      return sdkData.getTargets();
    }

    return new IAndroidTarget[0];
  }

  public static boolean isLayoutLibTarget(@NotNull IAndroidTarget target) {
    return target.isPlatform() && target.hasRenderingLibrary();
  }

  @Nullable
  public IAndroidTarget getHighestApiTarget() {
    // Note: The target list is already sorted in ascending API order.
    IAndroidTarget[] targetList = getTargets();
    for (int i = targetList.length - 1; i >= 0; i--) {
      IAndroidTarget target = targetList[i];
      if (isLayoutLibTarget(target) && isLayoutLibSupported(target)) {
        return target;
      }
    }

    return null;
  }

  /**
   * Returns if the LayoutLib API (not to be confused with Platform API) level is supported.
   */
  private static boolean isLayoutLibSupported(IAndroidTarget target) {
    if (target instanceof PlatformTarget) {
      int layoutlibVersion = ((PlatformTarget)target).getLayoutlibApi();
      return layoutlibVersion <= Bridge.API_CURRENT;
    }
    return false;
  }

  /**
   * Try to get activity theme from manifest. If no theme is found, We fall back to the app theme. If that isn't found,
    * we use the default system theme.
   */
  @NotNull
  public String computePreferredTheme(@NotNull Configuration configuration) {
    // TODO: If we are rendering a layout in included context, pick the theme from the outer layout instead.
    String activityName = configuration.getActivity();
    if (activityName != null) {
      String activityFqcn = activityName;
      if (activityName.startsWith(".")) {
        String packageName = ProjectSystemUtil.getModuleSystem(myModule).getPackageName();
        activityFqcn = packageName + activityName;
      }

      String theme = ThemeUtils.getThemeNameForActivity(myModule, activityFqcn);
      if (theme != null) {
        return theme;
      }
    }

    // Returns an app theme if possible
    String appTheme = ThemeUtils.getAppThemeName(myModule);
    if (appTheme != null) {
      return appTheme;
    }

    // Look up the default/fallback theme to use for this project (which depends on the screen size when no particular
    // theme is specified in the manifest).
    return ThemeUtils.getDefaultTheme(myModule, configuration.getTarget(), configuration.getScreenSize(), configuration.getCachedDevice());
  }

  @NotNull
  public final Module getModule() {
    return myModule;
  }

  @NotNull
  public Project getProject() {
    return myModule.getProject();
  }

  @Override
  public void dispose() {
    myModule.putUserData(KEY, null);
  }

  @Nullable
  public Device getDefaultDevice() {
    if (myDefaultDevice == null) {
      // Note that this may not be the device actually used in new layouts; the ConfigMatcher
      // has a PhoneComparator which sorts devices for a best match
      List<Device> devices = getDevices();
      if (!devices.isEmpty()) {
        Device device = devices.get(0);
        for (Device d : devices) {
          String id = d.getId();
          if (id.equals("pixel")) {
            device = d;
            break;
          }
          else if (id.equals("Galaxy Nexus")) {
            device = d;
          }
        }

        myDefaultDevice = device;
      }
    }

    return myDefaultDevice;
  }

  /**
   * Return the default render target to use, or null if no strong preference
   */
  @Nullable
  public IAndroidTarget getDefaultTarget() {
    // Use the most recent target
    return getHighestApiTarget();
  }

  @NotNull
  public ImmutableList<Locale> getLocalesInProject() {
    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myModule);
    assert repositoryManager != null;
    return repositoryManager.getLocalesInProject();
  }

  @Nullable
  public IAndroidTarget getProjectTarget() {
    AndroidPlatform platform = AndroidPlatforms.getInstance(myModule);
    return platform != null ? platform.getTarget() : null;
  }

  @NotNull
  public Locale getLocale() {
    if (myLocale == null) {
      String localeString = getStateManager().getProjectState().getLocale();
      if (localeString != null) {
        myLocale = ConfigurationProjectState.fromLocaleString(localeString);
      }
      else {
        myLocale = Locale.ANY;
      }
    }

    return myLocale;
  }

  public void setLocale(@NotNull Locale locale) {
    if (!locale.equals(myLocale)) {
      myLocale = locale;
      myStateVersion++;
      getStateManager().getProjectState().setLocale(ConfigurationProjectState.toLocaleString(locale));
      for (Configuration configuration : myCache.values()) {
        configuration.updated(CFG_LOCALE);
      }
    }
  }

  /**
   * Returns the most recently used devices, in MRU order
   */
  public List<Device> getRecentDevices() {
    List<String> deviceIds = getStateManager().getProjectState().getDeviceIds();
    if (deviceIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<Device> devices = Lists.newArrayListWithExpectedSize(deviceIds.size());
    ListIterator<String> iterator = deviceIds.listIterator();
    while (iterator.hasNext()) {
      String id = iterator.next();
      Device device = getDeviceById(id);
      if (device != null) {
        devices.add(device);
      }
      else {
        iterator.remove();
      }
    }

    return devices;
  }

  public void selectDevice(@NotNull Device device) {
    // Manually move the given device to the front of the eligibility queue
    String id = device.getId();
    List<String> deviceIds = getStateManager().getProjectState().getDeviceIds();
    deviceIds.remove(id);
    deviceIds.add(0, id);

    // Only store a limited number of recent devices
    while (deviceIds.size() > 10) {
      deviceIds.remove(deviceIds.size() - 1);
    }

    myStateVersion++;
    for (Configuration configuration : myCache.values()) {
      // TODO: Null out the themes too if using a system theme (e.g. where the theme was not chosen
      // by the activity or manifest default, but inferred based on the device and API level).
      // For example, if you switch from an Android Wear device (where the default is DeviceDefault) to
      // a Nexus 5 (where the default is currently Theme.Holo) we should recompute the theme for the
      // configuration too!
      boolean updateTheme = false;
      String theme = configuration.getTheme();
      if (theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
        updateTheme = true;
        configuration.startBulkEditing();
        configuration.setTheme(null);
      }
      configuration.setDevice(device, true);

      if (updateTheme) {
        configuration.finishBulkEditing();
      }
    }
  }

  @Nullable
  public IAndroidTarget getTarget() {
    if (myTarget == null) {
      ConfigurationProjectState projectState = getStateManager().getProjectState();
      if (projectState.isPickTarget()) {
        myTarget = getDefaultTarget();
      }
      else {
        String targetString = projectState.getTarget();
        myTarget = ConfigurationProjectState.fromTargetString(this, targetString);
        if (myTarget == null) {
          myTarget = getDefaultTarget();
        }
      }
      return myTarget;
    }

    return myTarget;
  }

  /**
   * Returns the best render target to use for the given minimum API level
   */
  @Nullable
  public IAndroidTarget getTarget(int min) {
    IAndroidTarget target = getTarget();
    if (target != null && target.getVersion().getApiLevel() >= min) {
      return target;
    }

    IAndroidTarget[] targetList = getTargets();
    for (int i = targetList.length - 1; i >= 0; i--) {
      target = targetList[i];
      if (isLayoutLibTarget(target) && target.getVersion().getFeatureLevel() >= min && isLayoutLibSupported(target)) {
        return target;
      }
    }

    return null;
  }

  public void setTarget(@Nullable IAndroidTarget target) {
    if (target != myTarget) {
      if (myTarget != null) {
        // Clear out the bitmap cache of the previous platform, since it's likely we won't
        // need it again. If you have *two* projects open with different platforms, this will
        // needlessly flush the bitmap cache for the project still using it, but that just
        // means the next render will need to fetch them again; from that point on both platform
        // bitmap sets are in memory.
        AndroidTargetData targetData = AndroidTargetData.getTargetData(myTarget, myModule);
        if (targetData != null) {
          targetData.clearLayoutBitmapCache(myModule);
        }
      }

      myTarget = target;
      if (target != null) {
        getStateManager().getProjectState().setTarget(ConfigurationProjectState.toTargetString(target));
        myStateVersion++;
        for (Configuration configuration : myCache.values()) {
          configuration.updated(CFG_TARGET);
        }
      }
    }
  }

  public int getStateVersion() {
    return myStateVersion;
  }

  @NotNull
  public ResourceResolverCache getResolverCache() {
    if (myResolverCache == null) {
      myResolverCache = new ResourceResolverCache(this);
    }

    return myResolverCache;
  }
}
