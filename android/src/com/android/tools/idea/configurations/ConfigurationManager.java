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

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakValueHashMap;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.uipreview.UserDeviceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.sdklib.devices.DeviceManager.DEFAULT_DEVICES;
import static com.android.sdklib.devices.DeviceManager.VENDOR_DEVICES;

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
  @NotNull private final Module myModule;
  private List<Device> myDevices;
  private List<String> myProjectThemes;
  private List<IAndroidTarget> myTargets;
  private final UserDeviceManager myUserDeviceManager;
  private final WeakValueHashMap<VirtualFile, Configuration> myCache = new WeakValueHashMap<VirtualFile, Configuration>();
  private List<Locale> myLocales;
  private Device myDefaultDevice;

  private ConfigurationManager(@NotNull Module module) {
    myModule = module;

    myUserDeviceManager = new UserDeviceManager() {
      @Override
      protected void userDevicesChanged() {
        // Force refresh
        myDevices = null;
        // TODO: How do I trigger changes in the UI?
      }
    };
    Disposer.register(this, myUserDeviceManager);
  }

  /**
   * Gets the {@link Configuration} associated with the given file
   * @return the {@link Configuration} for the given file
   */
  @NotNull
  public Configuration getConfiguration(@NotNull VirtualFile file) {
    Configuration configuration = myCache.get(file);
    if (configuration == null) {
      configuration = create(file);
      myCache.put(file, configuration);
    }

    return configuration;
  }

  /**
   * Creates a new {@link Configuration} associated with this manager
   * @return a new {@link Configuration}
   */
  @NotNull
  private Configuration create(@NotNull VirtualFile file) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationProjectState projectState = stateManager.getProjectState();
    ConfigurationFileState fileState = stateManager.getConfigurationState(file);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, projectState, file, fileState, config);
    ProjectResources projectResources = ProjectResources.get(myModule, true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, projectResources, file);
    if (fileState != null) {
      matcher.adaptConfigSelection(true);
    } else {
      matcher.findAndSetCompatibleConfig(false);
    }

    return configuration;
  }

  /**
   * Similar to {@link #getConfiguration(com.intellij.openapi.vfs.VirtualFile)}, but creates a configuration
   * for a file known to be new, and crucially, bases the configuration on the existing configuration
   * for a known file. This is intended for when you fork a layout, and you expect the forked layout
   * to have a configuration that is (as much as possible) similar to the configuration of the
   * forked file. For example, if you create a landscape version of a layout, it will preserve the
   * screen size, locale, theme and render target of the existing layout.
   *
   * @param file the file to create a configuration for
   * @param baseFile the other file to base the configuration on
   * @return the new configuration
   */
  @NotNull
  public Configuration createSimilar(@NotNull VirtualFile file, @NotNull VirtualFile baseFile) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationProjectState projectState = stateManager.getProjectState();
    ConfigurationFileState fileState = stateManager.getConfigurationState(baseFile);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, projectState, file, fileState, config);
    ProjectResources projectResources = ProjectResources.get(myModule, true);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, projectResources, file);
    matcher.adaptConfigSelection(true /*needBestMatch*/);
    myCache.put(file, configuration);

    return configuration;
  }

  /** Returns the associated persistence manager */
  public ConfigurationStateManager getStateManager() {
    return ConfigurationStateManager.get(myModule.getProject());
  }

  /**
   * Creates a new {@link ConfigurationManager} for the given module
   *
   * @param module the associated module
   * @return a new {@link ConfigurationManager}
   */
  @NotNull
  public static ConfigurationManager create(@NotNull Module module) {
    return new ConfigurationManager(module);
  }

  @Nullable
  private AndroidPlatform getPlatform() {
    // TODO: How do we refresh this if the user remaps chosen target?
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    if (sdk == null) {
      sdk = AndroidFacetConfiguration.findAndSetAndroidSdk(myModule);
    }
    if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        return additionalData.getAndroidPlatform();
      }
    }
    return null;
  }

  /** Returns the list of available devices for the current platform, if any */
  @NotNull
  public List<Device> getDevices() {
    if (myDevices == null) {
      List<Device> devices = null;

      AndroidPlatform platform = getPlatform();
      if (platform != null) {
        final AndroidSdkData sdkData = platform.getSdkData();
        devices = new ArrayList<Device>();
        DeviceManager deviceManager = sdkData.getDeviceManager();
        devices.addAll(deviceManager.getDevices((DEFAULT_DEVICES | VENDOR_DEVICES)));
        devices.addAll(myUserDeviceManager.parseUserDevices(new MessageBuildingSdkLog()));
      }

      if (devices == null) {
        myDevices = Collections.emptyList();
      } else {
        myDevices = devices;
      }
    }

    return myDevices;
  }

  @Nullable
  public Device createDeviceForAvd(@NotNull AvdInfo avd) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    for (Device device : getDevices()) {
      if (device.getManufacturer().equals(avd.getDeviceManufacturer())
          && device.getName().equals(avd.getDeviceName())) {

        String avdName = avd.getName();
        Device.Builder builder = new Device.Builder(device);
        builder.setName(avdName);
        return builder.build();
      }
    }

    return null;
  }

  @NotNull
  public List<IAndroidTarget> getTargets() {
    if (myTargets == null) {
      List<IAndroidTarget> targets = new ArrayList<IAndroidTarget>();

      AndroidPlatform platform = getPlatform();
      if (platform != null) {
        final AndroidSdkData sdkData = platform.getSdkData();

        for (IAndroidTarget target : sdkData.getTargets()) {
          if (target.isPlatform() && target.hasRenderingLibrary()) {
            targets.add(target);
          }
        }
      }

      myTargets = targets;
    }

    return myTargets;
  }

  /**
   * Returns the preferred theme, or null
   */
  @NotNull
  public String computePreferredTheme(@NotNull Configuration configuration) {
    ManifestInfo manifest = ManifestInfo.get(myModule);

    // TODO: If we are rendering a layout in included context, pick the theme
    // from the outer layout instead

    String activity = configuration.getActivity();
    if (activity != null) {
      Map<String, String> activityThemes = manifest.getActivityThemes();
      String theme = activityThemes.get(activity);
      if (theme != null) {
        return theme;
      }
    }

    // Look up the default/fallback theme to use for this project (which
    // depends on the screen size when no particular theme is specified
    // in the manifest)
    return manifest.getDefaultTheme(configuration.getTarget(), configuration.getScreenSize());
  }

  @NotNull
  public List<String> getProjectThemes() {
    if (myProjectThemes == null) {
      // TODO: How do we invalidate this if the manifest theme set changes?
      myProjectThemes = computeProjectThemes();
    }

    return myProjectThemes;
  }

  @NotNull
  private List<String> computeProjectThemes() {
    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      return Collections.emptyList();
    }

    final List<String> themes = new ArrayList<String>();
    final Map<String, ResourceElement> styleMap = buildStyleMap(facet);

    for (ResourceElement style : styleMap.values()) {
      if (isTheme(style, styleMap, new HashSet<ResourceElement>())) {
        final String themeName = style.getName().getValue();
        if (themeName != null) {
          final String theme = SdkConstants.STYLE_RESOURCE_PREFIX + themeName;
          themes.add(theme);
        }
      }
    }

    Collections.sort(themes);
    return themes;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public Project getProject() {
    return myModule.getProject();
  }

  @Override
  public void dispose() {
    myUserDeviceManager.dispose();
  }

  private static Map<String, ResourceElement> buildStyleMap(AndroidFacet facet) {
    final Map<String, ResourceElement> result = new HashMap<String, ResourceElement>();
    final List<ResourceElement> styles = facet.getLocalResourceManager().getValueResources(ResourceType.STYLE.getName());
    for (ResourceElement style : styles) {
      final String styleName = style.getName().getValue();
      if (styleName != null) {
        result.put(styleName, style);
      }
    }
    return result;
  }

  private static boolean isTheme(ResourceElement resElement, Map<String, ResourceElement> styleMap, Set<ResourceElement> visitedElements) {
    if (!visitedElements.add(resElement)) {
      return false;
    }

    if (!(resElement instanceof Style)) {
      return false;
    }

    final String styleName = resElement.getName().getValue();
    if (styleName == null) {
      return false;
    }

    final ResourceValue parentStyleRef = ((Style)resElement).getParentStyle().getValue();
    String parentStyleName = null;
    boolean frameworkStyle = false;

    if (parentStyleRef != null) {
      final String s = parentStyleRef.getResourceName();
      if (s != null) {
        parentStyleName = s;
        frameworkStyle = AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(parentStyleRef.getPackage());
      }
    }

    if (parentStyleRef == null) {
      final int index = styleName.indexOf('.');
      if (index >= 0) {
        parentStyleName = styleName.substring(0, index);
      }
    }

    if (parentStyleRef != null) {
      if (frameworkStyle) {
        return parentStyleName.equals("Theme") || parentStyleName.startsWith("Theme.");
      }
      else {
        final ResourceElement parentStyle = styleMap.get(parentStyleName);
        if (parentStyle != null) {
          return isTheme(parentStyle, styleMap, visitedElements);
        }
      }
    }

    return false;
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
          String name = d.getName();
          if (name.equals("Nexus 4")) {
            device = d;
            break;
          } else if (name.equals("Galaxy Nexus")) {
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
    List<IAndroidTarget> targetList = getTargets();
    for (int i = targetList.size() - 1; i >= 0; i--) {
      IAndroidTarget target = targetList.get(i);
      if (target.hasRenderingLibrary()) {
        return target;
      }
    }

    return null;
  }

  @NotNull
  public List<Locale> getLocales() {
    if (myLocales == null) {
      List<Locale> locales = new ArrayList<Locale>();
      ProjectResources projectResources = ProjectResources.get(myModule, true);
      for (String language : projectResources.getLanguages()) {
        LanguageQualifier languageQualifier = new LanguageQualifier(language);
        locales.add(Locale.create(languageQualifier));
        for (String region : projectResources.getRegions(language)) {
          locales.add(Locale.create(languageQualifier, new RegionQualifier(region)));
        }
      }
      myLocales = locales;
    }

    return myLocales;
  }

  @Nullable
  public IAndroidTarget getProjectTarget() {
    AndroidPlatform platform = getPlatform();
    return platform != null ? platform.getTarget() : null;
  }

  @NotNull
  public Locale getLocale() {
    String localeString = getStateManager().getProjectState().getLocale();
    if (localeString != null) {
      return ConfigurationProjectState.fromLocaleString(localeString);
    }

    return Locale.ANY;
  }

  public void setLocale(@NotNull Locale locale) {
    getStateManager().getProjectState().setLocale(ConfigurationProjectState.toLocaleString(locale));
  }

  @Nullable
  public IAndroidTarget getTarget() {
    String targetString = getStateManager().getProjectState().getTarget();
    IAndroidTarget target = ConfigurationProjectState.fromTargetString(this, targetString);
    if (target == null) {
      target = getDefaultTarget();
    }
    return target;
  }

  public void setTarget(@NotNull IAndroidTarget target) {
    getStateManager().getProjectState().setTarget(ConfigurationProjectState.toTargetString(target));
  }

  /**
   * Synchronizes changes to the given attributes (indicated by the mask
   * referencing the {@code CFG_} configuration attribute bit flags in
   * {@link Configuration} to the layout variations of the given updated file.
   *
   * @param flags the attributes which were updated
   * @param updatedFile the file which was updated
   * @param base the base configuration to base the chooser off of
   * @param includeSelf whether the updated file itself should be updated
   * @param async whether the updates should be performed asynchronously
   */
  public void syncToVariations(
    final int flags,
    final @NotNull VirtualFile updatedFile,
    final @NotNull Configuration base,
    final boolean includeSelf,
    boolean async) {
    if (async) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          doSyncToVariations(flags, updatedFile, includeSelf, base);
        }
      });
    } else {
      doSyncToVariations(flags, updatedFile, includeSelf, base);
    }
  }

  private void doSyncToVariations(int flags, VirtualFile updatedFile, boolean includeSelf,
                                  Configuration base) {
    // Synchronize the given changes to other configurations as well
    Project project = getProject();
    List<VirtualFile> files = ResourceHelper.getResourceVariations(updatedFile, includeSelf);
    for (VirtualFile file : files) {
      Configuration configuration = getConfiguration(file);
      Configuration.copyCompatible(base, configuration);
      configuration.save();
    }
  }
}
