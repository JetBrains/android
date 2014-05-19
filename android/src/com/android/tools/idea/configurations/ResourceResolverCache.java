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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.utils.SparseArray;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;

/** Cache for resolved resources */
public class ResourceResolverCache {
  private static final Logger LOG = Logger.getInstance(ResourceResolverCache.class);

  /** The configuration manager this cache corresponds to */
  private final ConfigurationManager myManager;

  /** Map from theme and full configuration to the corresponding resource resolver */
  private final Map<String, ResourceResolver> myResolverMap;

  /**
   * Map of configured app resources. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   * Note that they key here is only the full configuration, whereas the map for the
   * resolvers also includes the theme.
   */
  private final Map<String, Map<ResourceType, Map<String, ResourceValue>>> myAppResourceMap;

  /**
   * Map of configured framework resources. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme
   */
  private final Map<String, Map<ResourceType, Map<String, ResourceValue>>> myFrameworkResourceMap;

  /** The generation timestamp of our most recently cached app resources, used to invalidate on edits */
  private long myCachedGeneration;

  /** Map from API level to framework resources */
  private SparseArray<FrameworkResources> myFrameworkResources = new SparseArray<FrameworkResources>();

  public ResourceResolverCache(ConfigurationManager manager) {
    myManager = manager;
    myResolverMap = Maps.newHashMap();
    myAppResourceMap = Maps.newHashMap();
    myFrameworkResourceMap = Maps.newHashMap();
  }

  public static ResourceResolverCache create(ConfigurationManager manager) {
    return new ResourceResolverCache(manager);
  }

  @NotNull
  public ResourceResolver getResourceResolver(@Nullable IAndroidTarget target,
                                              @NotNull String themeStyle,
                                              @NotNull final FolderConfiguration fullConfiguration) {
    // Are caches up to date?
    final LocalResourceRepository resources = AppResourceRepository.getAppResources(myManager.getModule(), true);
    assert resources != null;
    if (myCachedGeneration != resources.getModificationCount()) {
      myResolverMap.clear();
      myAppResourceMap.clear();
    }

    // When looking up the configured project and framework resources, the theme doesn't matter, so we look up only
    // by the configuration qualifiers; for example, here's a sample key:
    // -ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    // Note that the target version is already baked in via the -v qualifier.
    //
    // However, the resource resolver also depends on the theme, so we use a more specific key for the resolver map than
    // for the configured resource maps, by prepending the theme name:
    // @style/MyTheme-ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    String configurationKey = fullConfiguration.getUniqueKey();
    String resolverKey = themeStyle + configurationKey;
    ResourceResolver resolver = myResolverMap.get(resolverKey);
    if (resolver == null) {
      Map<ResourceType, Map<String, ResourceValue>> configuredAppRes;
      Map<ResourceType, Map<String, ResourceValue>> frameworkResources;

      // Framework resources
      if (target == null) {
        target = myManager.getTarget();
      }
      if (target == null) {
        frameworkResources = Collections.emptyMap();
      } else {
        ResourceRepository frameworkRes = getFrameworkResources(fullConfiguration, target);
        if (frameworkRes == null) {
          frameworkResources = Collections.emptyMap();
        }
        else {
          // get the framework resource values based on the current config
          frameworkResources = myFrameworkResourceMap.get(configurationKey);
          if (frameworkResources == null) {
            frameworkResources = frameworkRes.getConfiguredResources(fullConfiguration);
            myFrameworkResourceMap.put(configurationKey, frameworkResources);
          }
        }
      }

      // App resources
      configuredAppRes = myAppResourceMap.get(configurationKey);
      if (configuredAppRes == null) {
        // get the project resource values based on the current config
        Application application = ApplicationManager.getApplication();
        configuredAppRes = application.runReadAction(new Computable<Map<ResourceType, Map<String, ResourceValue>>>() {
          @Override
          public Map<ResourceType, Map<String, ResourceValue>> compute() {
            return resources.getConfiguredResources(fullConfiguration);
          }
        });
        myAppResourceMap.put(configurationKey, configuredAppRes);
      }

      // Resource Resolver
      assert themeStyle.startsWith(STYLE_RESOURCE_PREFIX) || themeStyle.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) : themeStyle;
      boolean isProjectTheme = ResourceHelper.isProjectStyle(themeStyle);
      String themeName = ResourceHelper.styleToTheme(themeStyle);
      resolver = ResourceResolver.create(configuredAppRes, frameworkResources, themeName, isProjectTheme);

      myResolverMap.put(resolverKey, resolver);
      myCachedGeneration = resources.getModificationCount();
    }

    return resolver;
  }

  /**
   * Returns a {@link com.android.tools.idea.rendering.LocalResourceRepository} for the framework resources based on the current
   * configuration selection.
   *
   * @return the framework resources or null if not found.
   */
  @Nullable
  public ResourceRepository getFrameworkResources(@NotNull FolderConfiguration configuration, @NotNull IAndroidTarget target) {
    int apiLevel = target.getVersion().getApiLevel();
    FrameworkResources resources = myFrameworkResources.get(apiLevel);

    boolean reset = false;
    boolean needLocales = configuration.getLanguageQualifier() != null &&
                          !configuration.getLanguageQualifier().hasFakeValue() || myManager.getLocale() != Locale.ANY;
    if (resources instanceof FrameworkResourceLoader.IdeFrameworkResources) {
      if (needLocales && ((FrameworkResourceLoader.IdeFrameworkResources)resources).getSkippedLocales()) {
        reset = true;
      }
    }

    if (resources == null || reset) {
      FrameworkResourceLoader.requestLocales(needLocales);
      resources = getFrameworkResources(target, myManager.getModule(), reset);
      myFrameworkResources.put(apiLevel, resources);
    }

    return resources;
  }

  /**
   * Returns a {@link com.android.tools.idea.rendering.LocalResourceRepository} for the framework resources of a given
   * target.
   *
   * @param target the target for which to return the framework resources.
   * @return the framework resources or null if not found.
   */
  @Nullable
  private static FrameworkResources getFrameworkResources(@NotNull IAndroidTarget target, @NotNull Module module, boolean forceReload) {
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
      if (forceReload) {
        targetData.resetFrameworkResources();
      }
      return targetData.getFrameworkResources();
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }
}
