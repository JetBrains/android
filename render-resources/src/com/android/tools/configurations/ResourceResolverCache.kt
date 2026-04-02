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
package com.android.tools.configurations

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.ide.common.util.DisjointUnionMap
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.sdklib.IAndroidTarget
import com.android.tools.res.FrameworkOverlay
import com.android.tools.sdk.AndroidTargetData
import com.android.tools.sdk.CompatibilityRenderTarget
import com.android.utils.SparseArray
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Table

/** Cache for resolved resources. */
// TODO(namespaces): Cache AAR contents if namespaces are used.
class ResourceResolverCache(
  /** The configuration manager this cache corresponds to. */
  private val settings: ConfigurationSettings
) {
  private val lock = Any()

  /** Map from theme and full configuration to the corresponding resource resolver. */
  @VisibleForTesting @GuardedBy("lock") val resolverMap: MutableMap<String, ResourceResolver> = HashMap()

  /**
   * Map of configured app resources. These are cached separately from the final resource resolver since they can be shared between
   * different layouts that only vary by theme. Note that they key here is only the full configuration, whereas the map for the resolvers
   * also includes the theme.
   */
  @VisibleForTesting
  @GuardedBy("lock")
  val appResourceMap: MutableMap<String, Table<ResourceNamespace, ResourceType, ResourceValueMap>> = HashMap()

  /**
   * Map of configured resources from Android framework. These are cached separately from the final resource resolver since they can be
   * shared between different layouts that only vary by theme.
   */
  @VisibleForTesting @GuardedBy("lock") val frameworkResourceMap: MutableMap<String, Map<ResourceType, ResourceValueMap>> = HashMap()

  /** The generation timestamp of our most recently cached app resources, used to invalidate on edits. */
  @GuardedBy("lock") private var cachedGeneration: Long = 0

  /** Map from API level to framework resources. */
  @GuardedBy("lock") private val frameworkResources = SparseArray<AndroidTargetData>()

  /**
   * Store map keys for the latest custom configuration cached, so that they can be removed from the cache when a new custom configuration
   * is created. We only want to keep the latest one.
   */
  @GuardedBy("lock") private var customConfigurationKey: String? = null

  @GuardedBy("lock") private var customOverlaysKey: String? = null

  @GuardedBy("lock") private var customResolverKey: String? = null

  /**
   * Returns the cached [ResourceResolver] for the given configuration only if it exists. If it doesn't this method does not create it and
   * will return null.
   */
  internal fun getCachedResourceResolver(
    target: IAndroidTarget?,
    themeStyle: String,
    fullConfiguration: FolderConfiguration,
    overlays: List<FrameworkOverlay>,
  ): ResourceResolver? {
    settings.configModule.resourceRepositoryManager ?: return null

    val qualifierString = fullConfiguration.qualifierString
    val resolverKey = getResolverKey(themeStyle, qualifierString, overlays)
    return getCachedResolver(resolverKey)
  }

  /** Returns a [ResourceResolver]. If it does not exist yet, it creates it. */
  @Slow
  fun getResourceResolver(
    target: IAndroidTarget?,
    themeStyle: String,
    fullConfiguration: FolderConfiguration,
    overlays: List<FrameworkOverlay>,
  ): ResourceResolver {
    // Are caches up to date?
    val repositoryManager = settings.configModule.resourceRepositoryManager ?: return ResourceResolver.create(emptyMap(), null)

    val resources = repositoryManager.appResources
    synchronized(lock) {
      if (cachedGeneration != resources.modificationCount) {
        resolverMap.clear()
        appResourceMap.clear()
      }

      // Store the modification count as soon as possible. This ensures that if there is any modification of resources while the
      // resolver is being created, it will be cleared subsequently.
      cachedGeneration = resources.modificationCount
    }

    val qualifierString = fullConfiguration.qualifierString
    val resolverKey = getResolverKey(themeStyle, qualifierString, overlays)

    val existingResolver = getCachedResolver(resolverKey)
    if (existingResolver != null) {
      return existingResolver
    }

    val actualTarget = target ?: settings.target

    // Framework resources.
    val targetFrameworkResources =
      if (actualTarget == null) {
        emptyMap()
      } else {
        getConfiguredFrameworkResources(actualTarget, fullConfiguration, overlays)
      }

    // App resources
    val configuredAppRes =
      getCachedAppResources(qualifierString)
        ?: run {
          // Get the project resource values based on the current config.
          val newAppRes = resources.getConfiguredResources(fullConfiguration)
          cacheAppResources(qualifierString, newAppRes)
          newAppRes
        }

    // Resource Resolver
    val allResources: Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> =
      DisjointUnionMap(mapOf(ResourceNamespace.ANDROID to targetFrameworkResources), configuredAppRes.rowMap())

    // TODO(namespaces): the ResourceReference needs to be created by the caller, by resolving prefixes in the Manifest.
    var theme: ResourceReference? = null
    val themeUrl = ResourceUrl.parse(themeStyle)
    if (themeUrl != null) {
      val contextNamespace = repositoryManager.namespace
      theme = themeUrl.resolve(contextNamespace, ResourceNamespace.Resolver.EMPTY_RESOLVER)
    }

    val resolver = ResourceResolver.create(allResources, theme)

    if (actualTarget is CompatibilityRenderTarget) {
      val apiLevel = actualTarget.version.featureLevel
      if (apiLevel >= 21) {
        resolver.setDeviceDefaults("Material")
      } else if (apiLevel >= 14) {
        resolver.setDeviceDefaults("Holo")
      } else {
        resolver.setDeviceDefaults(ResourceResolver.LEGACY_THEME)
      }
    }

    cacheResourceResolver(resolverKey, resolver)

    return resolver
  }

  @Slow
  fun getConfiguredFrameworkResources(
    target: IAndroidTarget,
    fullConfiguration: FolderConfiguration,
    overlays: List<FrameworkOverlay>,
  ): Map<ResourceType, ResourceValueMap> {
    val resourceRepository = getFrameworkResources(fullConfiguration, target, overlays) ?: return emptyMap()

    val keyString = fullConfiguration.qualifierString + getOverlaysString(overlays)

    // Get the framework resource values based on the current config.
    val configuredFrameworkResources =
      getCachedFrameworkResources(keyString)
        ?: run {
          val newResources = resourceRepository.getConfiguredResources(fullConfiguration).row(ResourceNamespace.ANDROID)
          cacheFrameworkResources(keyString, newResources)
          newResources
        }
    return configuredFrameworkResources
  }

  /**
   * Returns the framework resource repository based on the current configuration selection.
   *
   * @return the framework resources or `null` if not found.
   */
  @Slow
  fun getFrameworkResources(
    configuration: FolderConfiguration,
    target: IAndroidTarget,
    overlays: List<FrameworkOverlay>,
  ): ResourceRepository? {
    val apiLevel = target.version.featureLevel

    val targetData =
      getCachedTargetData(apiLevel)
        ?: run {
          val platform = settings.configModule.androidPlatform ?: return null
          val newData = AndroidTargetData.get(platform.sdkData, target) // Uses soft reference.
          cacheTargetData(apiLevel, newData)
          newData
        }

    val locale = configuration.localeQualifier ?: settings.locale.qualifier
    val language = locale.language
    val languages = if (language == null) ImmutableSet.of() else ImmutableSet.of(language)
    return targetData.getFrameworkResources(languages, overlays)
  }

  fun reset() {
    synchronized(lock) {
      cachedGeneration = 0
      appResourceMap.clear()
      resolverMap.clear()
    }
  }

  /**
   * Replaces the custom configuration value in the resource resolver and removes the old custom configuration from the cache. If the new
   * configuration is the same as the old, this method will do nothing.
   *
   * @param themeStyle new theme
   * @param fullConfiguration new full configuration
   */
  fun replaceCustomConfig(themeStyle: String, fullConfiguration: FolderConfiguration, overlays: List<FrameworkOverlay>) {
    val overlayString = getOverlaysString(overlays)
    val qualifierString = fullConfiguration.qualifierString
    val newCustomResolverKey = getResolverKey(themeStyle, qualifierString, overlays)

    synchronized(lock) {
      if (newCustomResolverKey == customResolverKey) {
        // The new key is the same as this one, no need to remove it
        return
      }

      if (customConfigurationKey != null) {
        frameworkResourceMap.remove(customConfigurationKey + customOverlaysKey)
        appResourceMap.remove(customConfigurationKey)
      }
      if (customResolverKey != null) {
        resolverMap.remove(customResolverKey)
      }
      customConfigurationKey = qualifierString
      customOverlaysKey = overlayString
      customResolverKey = newCustomResolverKey
    }
  }

  private fun cacheTargetData(apiLevel: Int, targetData: AndroidTargetData) {
    synchronized(lock) { frameworkResources.put(apiLevel, targetData) }
  }

  private fun getCachedTargetData(apiLevel: Int): AndroidTargetData? {
    synchronized(lock) {
      return frameworkResources.get(apiLevel)
    }
  }

  private fun cacheFrameworkResources(qualifierString: String, configuredFrameworkResources: Map<ResourceType, ResourceValueMap>) {
    synchronized(lock) { frameworkResourceMap[qualifierString] = configuredFrameworkResources }
  }

  private fun getCachedFrameworkResources(qualifierString: String): Map<ResourceType, ResourceValueMap>? {
    synchronized(lock) {
      return frameworkResourceMap[qualifierString]
    }
  }

  private fun cacheAppResources(qualifierString: String, configuredAppResources: Table<ResourceNamespace, ResourceType, ResourceValueMap>) {
    synchronized(lock) { appResourceMap[qualifierString] = configuredAppResources }
  }

  private fun getCachedAppResources(qualifierString: String): Table<ResourceNamespace, ResourceType, ResourceValueMap>? {
    synchronized(lock) {
      return appResourceMap[qualifierString]
    }
  }

  private fun cacheResourceResolver(resolverKey: String, resolver: ResourceResolver) {
    synchronized(lock) { resolverMap[resolverKey] = resolver }
  }

  private fun getCachedResolver(resolverKey: String): ResourceResolver? {
    synchronized(lock) {
      return resolverMap[resolverKey]
    }
  }

  companion object {
    private fun getResolverKey(themeStyle: String, qualifierString: String, overlays: List<FrameworkOverlay>): String {
      // When looking up the configured project and framework resources, the theme doesn't matter, so we look up only
      // by the configuration qualifiers; for example, here's a sample key:
      // -ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
      // Note that the target version is already baked in via the -v qualifier.
      //
      // However, the resource resolver also depends on the theme, so we use a more specific key for the resolver map than
      // for the configured resource maps, by prepending the theme name:
      // @style/MyTheme-ldltr-sw384dp-w384dp-h640dp-normal-notlong-port-notnight-xhdpi-finger-keyssoft-nokeys-navhidden-nonav-1280x768-v17
      val baseKey =
        if (qualifierString.isEmpty()) {
          themeStyle
        } else {
          themeStyle + SdkConstants.RES_QUALIFIER_SEP + qualifierString
        }
      return baseKey + getOverlaysString(overlays)
    }

    private fun getOverlaysString(overlays: List<FrameworkOverlay>): String {
      return SdkConstants.RES_QUALIFIER_SEP + "Overlays:" + overlays.joinToString(SdkConstants.RES_QUALIFIER_SEP)
    }
  }
}
