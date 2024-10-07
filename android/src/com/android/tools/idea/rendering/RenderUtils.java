/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.StudioConfigurationModelModule;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.StudioResourceIdManager;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.rendering.classloading.ModuleClassLoaderManager;
import com.intellij.openapi.module.Module;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatforms;
import com.android.tools.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager;
import org.jetbrains.annotations.NotNull;

public class RenderUtils {
  public static void clearCache(@NotNull Collection<Configuration> configurations) {
    configurations
      .forEach(configuration -> {
        // Clear layoutlib bitmap cache (in case files have been modified externally)
        IAndroidTarget target = configuration.getTarget();
        Module module = ((StudioConfigurationModelModule)(configuration.getConfigModule())).getModule();
        StudioModuleClassLoaderManager.get().clearCache(module);
        StudioResourceIdManager.get(module).resetDynamicIds();
        ResourceClassRegistry.get(module.getProject()).clearCache();
        if (target != null) {
          AndroidTargetData targetData = AndroidTargetData.getTargetData(target, AndroidPlatforms.getInstance(module));
          if (targetData != null) {
            targetData.clearAllCaches(configuration.getConfigModule().getModuleKey());
          }

        // Reset resources for the current module and all the dependencies
        AndroidFacet facet = AndroidFacet.getInstance(module);
        Stream.concat(AndroidDependenciesCache.getAllAndroidDependencies(module, true).stream(), Stream.of(facet))
          .filter(Objects::nonNull)
          .forEach(f -> StudioResourceRepositoryManager.getInstance(f).resetAllCaches());
        }
      });
  }
}
