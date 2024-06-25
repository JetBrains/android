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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.util.DisjointUnionMap;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.res.CacheableResourceRepository;
import com.android.tools.res.FrameworkOverlay;
import com.android.tools.res.ResourceRepositoryManager;
import com.android.tools.sdk.AndroidPlatform;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.android.utils.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.Strings;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.android.tools.sdk.AndroidTargetData;

/** Cache for resolved resources. */
// TODO(namespaces): Cache AAR contents if namespaces are used.
public class ResourceResolverCache {
  /** The configuration manager this cache corresponds to. */
  private final ConfigurationSettings mySettings;
  private final Object myLock = new Object();

  /** Map from theme and full configuration to the corresponding resource resolver. */
  @VisibleForTesting
  @GuardedBy("myLock")
  public final Map<String, ResourceResolver> myResolverMap = new HashMap<>();

  /**
   * Map of configured app resources. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   * Note that they key here is only the full configuration, whereas the map for the
   * resolvers also includes the theme.
   */
  @VisibleForTesting
  @GuardedBy("myLock")
  public final Map<String, Table<ResourceNamespace, ResourceType, ResourceValueMap>> myAppResourceMap = new HashMap<>();

  /**
   * Map of configured resources from Android framework. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   */
  @VisibleForTesting
  @GuardedBy("myLock")
  public final Map<String, Map<ResourceType, ResourceValueMap>> myFrameworkResourceMap = new HashMap<>();

  /** The generation timestamp of our most recently cached app resources, used to invalidate on edits. */
  @GuardedBy("myLock")
  private long myCachedGeneration;

  /** Map from API level to framework resources */
  private final SparseArray<AndroidTargetData> myFrameworkResources = new SparseArray<>();

  /**
   * Store map keys for the latest custom configuration cached, so that they can be removed from the cache
   * when a new custom configuration is created. We only want to keep the latest one.
   */
  @GuardedBy("myLock")
  private String myCustomConfigurationKey;
  @GuardedBy("myLock")
  private String myCustomOverlaysKey;
  @GuardedBy("myLock")
  private String myCustomResolverKey;

  public ResourceResolverCache(ConfigurationSettings settings) {
    mySettings = settings;
  }

  @Slow
  public @NonNull ResourceResolver getResourceResolver(@Nullable IAndroidTarget target,
                                                       @NonNull String themeStyle,
                                                       @NonNull FolderConfiguration fullConfiguration,
                                                       @NonNull List<FrameworkOverlay> overlays) {
    // Are caches up to date?
    ResourceRepositoryManager repositoryManager = mySettings.getConfigModule().getResourceRepositoryManager();
    if (repositoryManager == null) {
      return ResourceResolver.create(Collections.emptyMap(), null);
    }
    CacheableResourceRepository resources = repositoryManager.getAppResources();
    synchronized (myLock) {
      if (myCachedGeneration != resources.getModificationCount()) {
        myResolverMap.clear();
        myAppResourceMap.clear();
      }

      // Store the modification count as soon as possible. This ensures that if there is any modification of resources while the
      // resolver is being created, it will be cleared subsequently.
      myCachedGeneration = resources.getModificationCount();
    }

    // When looking up the configured project and framework resources, the theme doesn't matter, so we look up only
    // by the configuration qualifiers; for example, here's a sample key:
    // -ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    // Note that the target version is already baked in via the -v qualifier.
    //
    // However, the resource resolver also depends on the theme, so we use a more specific key for the resolver map than
    // for the configured resource maps, by prepending the theme name:
    // @style/MyTheme-ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
    String qualifierString = fullConfiguration.getQualifierString();
    String resolverKey = getResolverKey(themeStyle, qualifierString, overlays);
    ResourceResolver resolver = getCachedResolver(resolverKey);
    if (resolver == null) {
      if (target == null) {
        target = mySettings.getTarget();
      }

      // Framework resources.
      Map<ResourceType, ResourceValueMap> frameworkResources =
          target == null ? Collections.emptyMap() : getConfiguredFrameworkResources(target, fullConfiguration, overlays);

      // App resources
      Table<ResourceNamespace, ResourceType, ResourceValueMap> configuredAppRes = getCachedAppResources(qualifierString);
      if (configuredAppRes == null) {
        // Get the project resource values based on the current config.
        configuredAppRes = ReadAction.compute(() -> ResourceRepositoryUtil.getConfiguredResources(resources, fullConfiguration));
        cacheAppResources(qualifierString, configuredAppRes);
      }

      // Resource Resolver
      Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> allResources =
          new DisjointUnionMap<>(Collections.singletonMap(ResourceNamespace.ANDROID, frameworkResources), configuredAppRes.rowMap());

      assert themeStyle.startsWith(PREFIX_RESOURCE_REF) : themeStyle;

      // TODO(namespaces): the ResourceReference needs to be created by the caller, by resolving prefixes in the Manifest.
      ResourceReference theme = null;
      ResourceUrl themeUrl = ResourceUrl.parse(themeStyle);
      if (themeUrl != null) {
        ResourceNamespace contextNamespace = repositoryManager.getNamespace();
        theme = themeUrl.resolve(contextNamespace, ResourceNamespace.Resolver.EMPTY_RESOLVER);
      }

      resolver = ResourceResolver.create(allResources, theme);

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

      cacheResourceResolver(resolverKey, resolver);
    }

    return resolver;
  }

  @Slow
  @NonNull
  public Map<ResourceType, ResourceValueMap> getConfiguredFrameworkResources(@NonNull IAndroidTarget target,
                                                                             @NonNull FolderConfiguration fullConfiguration,
                                                                             @NonNull List<FrameworkOverlay> overlays) {
    ResourceRepository resourceRepository = getFrameworkResources(fullConfiguration, target, overlays);
    if (resourceRepository == null) {
      return Collections.emptyMap();
    }

    String keyString = fullConfiguration.getQualifierString() + getOverlaysString(overlays);
    // Get the framework resource values based on the current config.
    Map<ResourceType, ResourceValueMap> frameworkResources = getCachedFrameworkResources(keyString);
    if (frameworkResources == null) {
      frameworkResources = ResourceRepositoryUtil.getConfiguredResources(resourceRepository, fullConfiguration).row(ResourceNamespace.ANDROID);
      cacheFrameworkResources(keyString, frameworkResources);
    }
    return frameworkResources;
  }

  @NonNull
  private static String getResolverKey(@NonNull String themeStyle, @NonNull String qualifierString,
                                       @NonNull List<FrameworkOverlay> overlays) {
    return (qualifierString.isEmpty() ? themeStyle : themeStyle + SdkConstants.RES_QUALIFIER_SEP + qualifierString)
           + getOverlaysString(overlays);
  }

  @NonNull
  private static String getOverlaysString(@NonNull List<FrameworkOverlay> overlays) {
    return SdkConstants.RES_QUALIFIER_SEP + "Overlays:" + Strings.join(overlays, SdkConstants.RES_QUALIFIER_SEP);
  }

  /**
   * Returns the framework resource repository based on the current configuration selection.
   *
   * @return the framework resources or {@code null} if not found.
   */
  @Slow
  @Nullable
  public ResourceRepository getFrameworkResources(@NonNull FolderConfiguration configuration, @NonNull IAndroidTarget target,
                                                  @NonNull List<FrameworkOverlay> overlays) {
    int apiLevel = target.getVersion().getFeatureLevel();

    AndroidTargetData targetData = getCachedTargetData(apiLevel);
    if (targetData == null) {
      AndroidPlatform platform = mySettings.getConfigModule().getAndroidPlatform();
      if (platform == null) {
        return null;
      }
      targetData = AndroidTargetData.get(platform.getSdkData(), target); // Uses soft reference.
      cacheTargetData(apiLevel, targetData);
    }

    LocaleQualifier locale = configuration.getLocaleQualifier();
    if (locale == null) {
      locale = mySettings.getLocale().qualifier;
    }
    String language = locale.getLanguage();
    Set<String> languages = language == null ? ImmutableSet.of() : ImmutableSet.of(language);
    return targetData.getFrameworkResources(languages, overlays);
  }

  public void reset() {
    synchronized (myLock) {
      myCachedGeneration = 0;
      myAppResourceMap.clear();
      myResolverMap.clear();
    }
  }

  /**
   * Replaces the custom configuration value in the resource resolver and removes the old custom configuration from the cache. If the new
   * configuration is the same as the old, this method will do nothing.
   *
   * @param themeStyle new theme
   * @param fullConfiguration new full configuration
   */
  public void replaceCustomConfig(@NonNull String themeStyle, @NonNull FolderConfiguration fullConfiguration,
                                  @NonNull List<FrameworkOverlay> overlays) {
    String overlayString = getOverlaysString(overlays);
    String qualifierString = fullConfiguration.getQualifierString();
    String newCustomResolverKey = getResolverKey(themeStyle, qualifierString, overlays);

    synchronized (myLock) {
      if (newCustomResolverKey.equals(myCustomResolverKey)) {
        // The new key is the same as this one, no need to remove it
        return;
      }

      if (myCustomConfigurationKey != null) {
        myFrameworkResourceMap.remove(myCustomConfigurationKey + myCustomOverlaysKey);
        myAppResourceMap.remove(myCustomConfigurationKey);
      }
      if (myCustomResolverKey != null) {
        myResolverMap.remove(myCustomResolverKey);
      }
      myCustomConfigurationKey = qualifierString;
      myCustomOverlaysKey = overlayString;
      myCustomResolverKey = newCustomResolverKey;
    }
  }

  private void cacheTargetData(int apiLevel, @NonNull AndroidTargetData targetData) {
    synchronized (myLock) {
      myFrameworkResources.put(apiLevel, targetData);
    }
  }

  private @Nullable AndroidTargetData getCachedTargetData(int apiLevel) {
    synchronized (myLock) {
      return myFrameworkResources.get(apiLevel);
    }
  }

  private void cacheFrameworkResources(@NonNull String qualifierString, @NonNull Map<ResourceType, ResourceValueMap> frameworkResources) {
    synchronized (myLock) {
      myFrameworkResourceMap.put(qualifierString, frameworkResources);
    }
  }

  private @Nullable Map<ResourceType, ResourceValueMap> getCachedFrameworkResources(@NonNull String qualifierString) {
    synchronized (myLock) {
      return myFrameworkResourceMap.get(qualifierString);
    }
  }

  private void cacheAppResources(
      @NonNull String qualifierString, @NonNull Table<ResourceNamespace, ResourceType, ResourceValueMap> configuredAppResources) {
    synchronized (myLock) {
      myAppResourceMap.put(qualifierString, configuredAppResources);
    }
  }

  private @Nullable Table<ResourceNamespace, ResourceType, ResourceValueMap> getCachedAppResources(@NonNull String qualifierString) {
    synchronized (myLock) {
      return myAppResourceMap.get(qualifierString);
    }
  }

  private void cacheResourceResolver(@NonNull String resolverKey, @NonNull ResourceResolver resolver) {
    synchronized (myLock) {
      myResolverMap.put(resolverKey, resolver);
    }
  }

  private @Nullable ResourceResolver getCachedResolver(@NonNull String resolverKey) {
    synchronized (myLock) {
      return myResolverMap.get(resolverKey);
    }
  }
}
