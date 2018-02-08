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
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.util.LazyUnionMap;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.FileResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ReadAction;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;

/** Cache for resolved resources. */
// TODO(namespaces): Cache AAR contents if namespaces are used.
public class ResourceResolverCache {
  /** The configuration manager this cache corresponds to. */
  private final ConfigurationManager myManager;

  /** Map from theme and full configuration to the corresponding resource resolver. */
  @VisibleForTesting
  final Map<String, ResourceResolver> myResolverMap = new HashMap<>();

  /**
   * Map of configured app resources. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   * Note that they key here is only the full configuration, whereas the map for the
   * resolvers also includes the theme.
   */
  @VisibleForTesting
  final Map<String, Table<ResourceNamespace, ResourceType, ResourceValueMap>> myAppResourceMap = new HashMap<>();

  /**
   * Map of configured resources from Android framework. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   */
  @VisibleForTesting
  final Map<String, Map<ResourceType, ResourceValueMap>> myFrameworkResourceMap = new HashMap<>();

  /** The generation timestamp of our most recently cached app resources, used to invalidate on edits. */
  private long myCachedGeneration;

  /** Map from API level to framework resources */
  private SparseArray<AndroidTargetData> myFrameworkResources = new SparseArray<>();

  /**
   * Store map keys for the latest custom configuration cached, so that they can be removed from the cache
   * when a new custom configuration is created. We only want to keep the latest one.
   */
  private String myCustomConfigurationKey;
  private String myCustomResolverKey;

  public ResourceResolverCache(ConfigurationManager manager) {
    myManager = manager;
  }

  @NotNull
  public ResourceResolver getResourceResolver(@Nullable IAndroidTarget target,
                                              @NotNull String themeStyle,
                                              @NotNull FolderConfiguration fullConfiguration) {
    // Are caches up to date?
    final AppResourceRepository resources = AppResourceRepository.getOrCreateInstance(myManager.getModule());
    if (resources == null) {
      return ResourceResolver.create(Collections.emptyMap(), null, false);
    }
    if (myCachedGeneration != resources.getModificationCount()) {
      myResolverMap.clear();
      myAppResourceMap.clear();
    }

    // Store the modification count as soon as possible. This ensures that if there is any modification of resources while the
    // resolver is being created, it will be cleared subsequently.
    myCachedGeneration = resources.getModificationCount();

    // When looking up the configured project and framework resources, the theme doesn't matter, so we look up only
    // by the configuration qualifiers; for example, here's a sample key:
    // -ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    // Note that the target version is already baked in via the -v qualifier.
    //
    // However, the resource resolver also depends on the theme, so we use a more specific key for the resolver map than
    // for the configured resource maps, by prepending the theme name:
    // @style/MyTheme-ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    String qualifierString = fullConfiguration.getQualifierString();
    String resolverKey = getResolverKey(themeStyle, qualifierString);
    ResourceResolver resolver = myResolverMap.get(resolverKey);
    if (resolver == null) {
      if (target == null) {
        target = myManager.getTarget();
      }

      // Framework resources.
      Map<ResourceType, ResourceValueMap> frameworkResources =
          target == null ? Collections.emptyMap() : getConfiguredFrameworkResources(target, fullConfiguration);

      // App resources
      Table<ResourceNamespace, ResourceType, ResourceValueMap> configuredAppRes = myAppResourceMap.get(qualifierString);
      if (configuredAppRes == null) {
        // Get the project resource values based on the current config.
        configuredAppRes = ReadAction.compute(() -> resources.getConfiguredResources(fullConfiguration));
        myAppResourceMap.put(qualifierString, configuredAppRes);
      }

      // Resource Resolver
      Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> allResources =
        new LazyUnionMap<>(Collections.singletonMap(ResourceNamespace.ANDROID, frameworkResources), configuredAppRes.rowMap());

      assert themeStyle.startsWith(PREFIX_RESOURCE_REF) : themeStyle;
      boolean isProjectTheme = ResourceHelper.isProjectStyle(themeStyle);
      String themeName = ResourceHelper.styleToTheme(themeStyle);
      resolver = ResourceResolver.create(allResources, themeName, isProjectTheme);

      resolver.setLibrariesIdProvider(new RenderResources.ResourceIdProvider() {
        @Override
        public Integer getId(ResourceType resType, String resName) {
          for (FileResourceRepository library : resources.getLibraries()) {
            Map<String, Integer> declaredIds = library.getAllDeclaredIds();
            if (declaredIds != null) {
              Integer id = declaredIds.get(resName);
              if (id != null) {
                return id;
              }
            }
          }

          return null;
        }
      });

      if (target instanceof CompatibilityRenderTarget) {
        int apiLevel = target.getVersion().getFeatureLevel();
        if (apiLevel >= 21) {
          resolver.setDeviceDefaults("Material");
        } else if (apiLevel >= 14) {
          resolver.setDeviceDefaults("Holo");
        } else {
          resolver.setDeviceDefaults(ResourceResolver.LEGACY_THEME);
        }
      }

      myResolverMap.put(resolverKey, resolver);
    }

    return resolver;
  }

  public Map<ResourceType, ResourceValueMap> getConfiguredFrameworkResources(@NotNull IAndroidTarget target,
                                                                             @NotNull FolderConfiguration fullConfiguration) {
    AbstractResourceRepository resourceRepository = getFrameworkResources(fullConfiguration, target);
    if (resourceRepository == null) {
      return Collections.emptyMap();
    }

    String qualifierString = fullConfiguration.getQualifierString();
    // Get the framework resource values based on the current config.
    Map<ResourceType, ResourceValueMap> frameworkResources = myFrameworkResourceMap.get(qualifierString);
    if (frameworkResources == null) {
      frameworkResources = resourceRepository.getConfiguredResources(fullConfiguration).row(ResourceNamespace.ANDROID);
      myFrameworkResourceMap.put(qualifierString, frameworkResources);
    }
    return frameworkResources;
  }

  @NotNull
  private static String getResolverKey(@NotNull String themeStyle, @NotNull String qualifierString) {
    return qualifierString.isEmpty() ? themeStyle : themeStyle + SdkConstants.RES_QUALIFIER_SEP + qualifierString;
  }

  /**
   * Returns the framework resource repository based on the current configuration selection.
   *
   * @return the framework resources or {@code null} if not found.
   */
  @Nullable
  public AbstractResourceRepository getFrameworkResources(@NotNull FolderConfiguration configuration, @NotNull IAndroidTarget target) {
    int apiLevel = target.getVersion().getFeatureLevel();

    AndroidTargetData targetData = myFrameworkResources.get(apiLevel);
    if (targetData == null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(myManager.getModule());
      if (platform == null) {
        return null;
      }
      targetData = platform.getSdkData().getTargetData(target); // Uses soft reference.
      myFrameworkResources.put(apiLevel, targetData);
    }

    // TODO: Michal Bendowski wrote:
    // I think we need to rework this. The whole idea of a ResourceRepository is that it contains all
    // ResourceItems for all configurations. ResourceResolver(Cache) is the layer that for a given
    // configuration turns them all into ResourceValues. So we should be able to just do something like:
    // ResourceRepositoryManager.getFrameworkResources(target).getConfiguredResources(configuration)
    // and cache the result. ResourceRepositoryManager should be the one caching the repositories accordingly?

    // Framework resources with locales are 10 times larger and take 10 times longer to load than without locales.
    // Avoid loading full framework resources whenever we can.
    LocaleQualifier locale = configuration.getLocaleQualifier();
    boolean needLocales = locale != null && !locale.hasFakeValue() || myManager.getLocale() != Locale.ANY;
    return targetData.getFrameworkResources(needLocales);
  }

  public void reset() {
    myCachedGeneration = 0;
    myAppResourceMap.clear();
    myResolverMap.clear();
  }

  /**
   * Replaces the custom configuration value in the resource resolver and removes the old custom configuration from the cache. If the new
   * configuration is the same as the old, this method will do nothing.
   *
   * @param themeStyle new theme
   * @param fullConfiguration new full configuration
   */
  public void replaceCustomConfig(@NotNull String themeStyle, @NotNull FolderConfiguration fullConfiguration) {
    String qualifierString = fullConfiguration.getQualifierString();
    String newCustomResolverKey = getResolverKey(themeStyle, qualifierString);

    if (newCustomResolverKey.equals(myCustomResolverKey)) {
      // The new key is the same as this one, no need to remove it
      return;
    }

    if (myCustomConfigurationKey != null) {
      myFrameworkResourceMap.remove(myCustomConfigurationKey);
      myAppResourceMap.remove(myCustomConfigurationKey);
    }
    if (myCustomResolverKey != null) {
      myResolverMap.remove(myCustomResolverKey);
    }
    myCustomConfigurationKey = qualifierString;
    myCustomResolverKey = newCustomResolverKey;
  }
}
