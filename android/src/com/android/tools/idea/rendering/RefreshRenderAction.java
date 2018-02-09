/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

public class RefreshRenderAction extends AnAction {
  private final EditorDesignSurface mySurface;

  public RefreshRenderAction(EditorDesignSurface surface) {
    super(AndroidBundle.message("android.layout.preview.refresh.action.text"), null, null);
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    clearCache(mySurface.getConfiguration());
    mySurface.forceUserRequestedRefresh();
  }

  public static void clearCacheAndRefreshSurface(@NotNull EditorDesignSurface surface) {
    clearCache(surface.getConfiguration());
    surface.forceUserRequestedRefresh();
  }

  public static void clearCache(@Nullable Configuration configuration) {
    ModuleClassLoader.clearCache();

    if (configuration != null) {
      // Clear layoutlib bitmap cache (in case files have been modified externally)
      IAndroidTarget target = configuration.getTarget();
      Module module = configuration.getModule();
      if (module != null) {
        ResourceIdManager.get(module).resetDynamicIds();
        if (target != null) {
          AndroidTargetData targetData = AndroidTargetData.getTargetData(target, module);
          if (targetData != null) {
            targetData.clearLayoutBitmapCache(module);
          }
        }

        // Reset resources for the current module and all the dependencies
        AndroidFacet facet = AndroidFacet.getInstance(module);
        Stream.concat(AndroidUtils.getAllAndroidDependencies(module, true).stream(), Stream.of(facet))
          .filter(Objects::nonNull)
          .forEach(f -> ResourceRepositoryManager.getOrCreateInstance(f).resetAllCaches());
      }

      configuration.updated(ConfigurationListener.MASK_RENDERING);
    }
  }
}
