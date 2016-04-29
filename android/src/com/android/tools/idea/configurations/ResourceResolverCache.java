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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.utils.SparseArray;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.android.SdkConstants.*;

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
  private SparseArray<AndroidTargetData> myFrameworkResources = new SparseArray<AndroidTargetData>();

  public ResourceResolverCache(ConfigurationManager manager) {
    myManager = manager;
    myResolverMap = Maps.newHashMap();
    myAppResourceMap = Maps.newHashMap();
    myFrameworkResourceMap = Maps.newHashMap();
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

            // Fix up assets. We're only doing this in limited cases for now; specifically Froyo (since the Gingerbread
            // assets replaced the look for the same theme; that doesn't happen to the same extend for Holo)
            if (target instanceof CompatibilityRenderTarget && target.getVersion().getApiLevel() == 8) {
              IAndroidTarget realTarget = ((CompatibilityRenderTarget)target).getRealTarget();
              if (realTarget != null) {
                replaceDrawableBitmaps(frameworkResources, target, realTarget);
              }
            }

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
      assert themeStyle.startsWith(PREFIX_RESOURCE_REF) : themeStyle;
      boolean isProjectTheme = ResourceHelper.isProjectStyle(themeStyle);
      String themeName = ResourceHelper.styleToTheme(themeStyle);
      resolver = ResourceResolver.create(configuredAppRes, frameworkResources, themeName, isProjectTheme);

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
   * Returns a {@link LocalResourceRepository} for the framework resources based on the current configuration selection.
   *
   * @return the framework resources or {@code null} if not found.
   */
  @Nullable
  public ResourceRepository getFrameworkResources(@NotNull FolderConfiguration configuration, @NotNull IAndroidTarget target) {
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

    try {
      return targetData.getFrameworkResources(needLocales);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  /**
   * Replaces drawable bitmaps with those from the real older target. This helps the simulated platform look more genuine,
   * since a lot of the look comes from the nine patch assets. For example, when used to simulate Froyo, the checkboxes
   * will look better than if we use the current classic theme assets, which look like gingerbread.
   */
  private static void replaceDrawableBitmaps(@NotNull Map<ResourceType, Map<String, ResourceValue>> frameworkResources,
                                             @NotNull IAndroidTarget from,
                                             @NotNull IAndroidTarget realTarget) {
    // This is a bit hacky; we should be operating at the resource repository level rather than
    // for configured resources. However, we may not need this for very long.
    Map<String, ResourceValue> map = frameworkResources.get(ResourceType.DRAWABLE);
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
}
