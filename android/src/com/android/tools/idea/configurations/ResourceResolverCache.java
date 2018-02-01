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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.util.LazyUnionMap;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.FileResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ReadAction;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;

/** Cache for resolved resources */
public class ResourceResolverCache {
  /** The configuration manager this cache corresponds to */
  private final ConfigurationManager myManager;

  /** Map from theme and full configuration to the corresponding resource resolver */
  @VisibleForTesting
  final Map<String, ResourceResolver> myResolverMap;

  /**
   * Map of configured app resources. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   * Note that they key here is only the full configuration, whereas the map for the
   * resolvers also includes the theme.
   */
  @VisibleForTesting
  final Map<String, Table<ResourceNamespace, ResourceType, ResourceValueMap>> myAppResourceMap;

  /**
   * Map of configured resources from external dependencies, including the framework. These are cached separately from the final resource
   * resolver since they can be shared between different layouts that only vary by theme.
   *
   * TODO(namespaces): Cache AAR contents here if we're using namespaces.
   */
  @VisibleForTesting
  final Map<String, Map<ResourceType, ResourceValueMap>> myExternalResourceMap;

  /** The generation timestamp of our most recently cached app resources, used to invalidate on edits */
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
    myResolverMap = Maps.newHashMap();
    myAppResourceMap = Maps.newHashMap();
    myExternalResourceMap = Maps.newHashMap();
  }

  @NotNull
  public ResourceResolver getResourceResolver(@Nullable IAndroidTarget target,
                                              @NotNull String themeStyle,
                                              @NotNull final FolderConfiguration fullConfiguration) {
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
    String configurationKey = fullConfiguration.getUniqueKey();
    String resolverKey = themeStyle + configurationKey;
    ResourceResolver resolver = myResolverMap.get(resolverKey);
    if (resolver == null) {
      Table<ResourceNamespace, ResourceType, ResourceValueMap> configuredAppRes;
      Map<ResourceType, ResourceValueMap> frameworkResources;

      // Framework resources
      if (target == null) {
        target = myManager.getTarget();
      }
      if (target == null) {
        frameworkResources = Collections.emptyMap();
      } else {
        AbstractResourceRepository frameworkRes = getFrameworkResources(fullConfiguration, target);
        if (frameworkRes == null) {
          frameworkResources = Collections.emptyMap();
        }
        else {
          // get the framework resource values based on the current config
          frameworkResources = myExternalResourceMap.get(configurationKey);
          if (frameworkResources == null) {
            frameworkResources = frameworkRes.getConfiguredResources(fullConfiguration).row(ResourceNamespace.ANDROID);

            // Fix up assets. We're only doing this in limited cases for now; specifically Froyo (since the Gingerbread
            // assets replaced the look for the same theme; that doesn't happen to the same extend for Holo)
            if (target instanceof CompatibilityRenderTarget && target.getVersion().getApiLevel() == 8) {
              IAndroidTarget realTarget = ((CompatibilityRenderTarget)target).getRealTarget();
              if (realTarget != null) {
                replaceDrawableBitmaps(frameworkResources, target, realTarget);
              }
            }

            myExternalResourceMap.put(configurationKey, frameworkResources);
          }
        }
      }

      // App resources
      configuredAppRes = myAppResourceMap.get(configurationKey);
      if (configuredAppRes == null) {
        // Get the project resource values based on the current config.
        configuredAppRes = ReadAction.compute(() -> resources.getConfiguredResources(fullConfiguration));
        myAppResourceMap.put(configurationKey, configuredAppRes);
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
            Integer id = library.getAllDeclaredIds().get(resName);

            if (id != null) {
              return id;
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

  /**
   * Returns the framework resource repository based on the current configuration selection.
   *
   * @return the framework resources or {@code null} if not found.
   */
  @Nullable
  public AbstractResourceRepository getFrameworkResources(@NotNull FolderConfiguration configuration, @NotNull IAndroidTarget target) {
    // TODO: Michal Bendowski wrote:
    // I think we need to rework this. The whole idea of a ResourceRepository is that it contains all
    // ResourceItems for all configurations. ResourceResolver(Cache) is the layer that for a given
    // configuration turns them all into ResourceValues. So we should be able to just do something like:
    // ResourceRepositoryManager.getFrameworkResources(target).getConfiguredResources(configuration)
    // and cache the result. ResourceRepositoryManager should be the one caching the repositories accordingly?
    int apiLevel = target.getVersion().getFeatureLevel();

    LocaleQualifier locale = configuration.getLocaleQualifier();
    boolean needLocales = locale != null && !locale.hasFakeValue() || myManager.getLocale() != Locale.ANY;

    AndroidTargetData targetData = myFrameworkResources.get(apiLevel);
    if (targetData == null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(myManager.getModule());
      if (platform == null) {
        return null;
      }
      targetData = platform.getSdkData().getTargetData(target); // uses soft ref
      myFrameworkResources.put(apiLevel, targetData);
    }

    return targetData.getFrameworkResources(needLocales);
  }

  /**
   * Replaces drawable bitmaps with those from the real older target. This helps the simulated platform look more genuine,
   * since a lot of the look comes from the nine patch assets. For example, when used to simulate Froyo, the checkboxes
   * will look better than if we use the current classic theme assets, which look like gingerbread.
   */
  private static void replaceDrawableBitmaps(@NotNull Map<ResourceType, ResourceValueMap> frameworkResources,
                                             @NotNull IAndroidTarget from,
                                             @NotNull IAndroidTarget realTarget) {
    // This is a bit hacky; we should be operating at the resource repository level rather than
    // for configured resources. However, we may not need this for very long.
    ResourceValueMap map = frameworkResources.get(ResourceType.DRAWABLE);
    String oldPrefix = from.getPath(IAndroidTarget.RESOURCES);
    String newPrefix = realTarget.getPath(IAndroidTarget.RESOURCES);

    if (map == null || map.isEmpty() || oldPrefix == null || newPrefix == null || oldPrefix.equals(newPrefix)) {
      return;
    }

    Collection<ResourceValue> values = map.values();
    Map<String,String> densityDirMap = Maps.newHashMap();

    // Leave XML drawable resources alone since they can reference nonexistent colors and other resources
    // not available in the real rendering platform
    final boolean ONLY_REPLACE_BITMAPS = true;
    Density[] densities = Density.values();
    for (ResourceValue value : values) {
      String v = value.getValue();
      //noinspection ConstantConditions,PointlessBooleanExpression
      if (v != null && (!ONLY_REPLACE_BITMAPS || v.endsWith(DOT_PNG))) {
        if (v.startsWith(oldPrefix)) {
          String relative = v.substring(oldPrefix.length());
          if (v.endsWith(DOT_PNG)) {
            int index = relative.indexOf(File.separatorChar);
            if (index == -1) {
              index = relative.indexOf('/');
            }
            if (index == -1) {
              continue;
            }
            String parent = relative.substring(0, index);
            String replace = densityDirMap.get(parent);
            if (replace == null) {
              FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(parent);
              if (configuration != null) {
                DensityQualifier densityQualifier = configuration.getDensityQualifier();
                if (densityQualifier != null) {
                  Density density = densityQualifier.getValue();
                  if (!new File(newPrefix, parent).exists()) {
                    String oldQualifier = SdkConstants.RES_QUALIFIER_SEP + density.getResourceValue();
                    String matched = null;
                    for (Density d : densities) {
                      if (d.ordinal() <= density.ordinal()) {
                        // No reason to check higher
                        continue;
                      }
                      String newQualifier = SdkConstants.RES_QUALIFIER_SEP + d.getResourceValue();
                      String newName = parent.replace(oldQualifier, newQualifier);
                      File dir = new File(newPrefix, newName);
                      if (dir.exists()) {
                        matched = newName;
                        break;
                      }
                    }
                    if (matched == null) {
                      continue;
                    }
                    replace = matched;
                    densityDirMap.put(parent, replace); // This isn't right; there may be some assets only in mdpi!
                  }
                }
              }
            }

            relative = replace + relative.substring(index);
          }

          File newFile = new File(newPrefix, relative);
          if (newFile.exists()) {
            value.setValue(newFile.getPath());
          }
        }
      }
    }
  }

  public void reset() {
    myCachedGeneration = 0;
    myAppResourceMap.clear();
    myResolverMap.clear();
  }

  /**
   * Replaces the custom configuration value in the resource resolver and removes the old custom configuration from the cache. If the new
   * configuration is the same as the old, this method will do nothing.
   * @param themeStyle new theme
   * @param fullConfiguration new full configuration
   */
  public void replaceCustomConfig(@NotNull String themeStyle, @NotNull final FolderConfiguration fullConfiguration) {
    String newCustomConfigurationKey = fullConfiguration.getUniqueKey();
    String newCustomResolverKey = themeStyle + newCustomConfigurationKey;

    if (newCustomResolverKey.equals(myCustomResolverKey)) {
      // The new key is the same as this one, no need to remove it
      return;
    }

    if (myCustomConfigurationKey != null) {
      myExternalResourceMap.remove(myCustomConfigurationKey);
      myAppResourceMap.remove(myCustomConfigurationKey);
    }
    if (myCustomResolverKey != null) {
      myResolverMap.remove(myCustomResolverKey);
    }
    myCustomConfigurationKey = newCustomConfigurationKey;
    myCustomResolverKey = newCustomResolverKey;
  }
}
