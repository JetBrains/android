/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.ProjectWideFacetAdapter;
import com.intellij.facet.ProjectWideFacetListenersRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class DeviceExplorer {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorer.class);
  private static final String DEVICE_EXPLORER_ENABLED = "android.device.explorer.enabled";
  private static boolean myEnabled;

  public static boolean isFeatureEnabled(@NotNull Project project) {
    boolean enabled = myEnabled || SystemProperties.getBooleanProperty(DEVICE_EXPLORER_ENABLED, false);
    if (!enabled) {
      return false;
    }

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      LOGGER.info("Project has no Android facet, delaying tool window creation.");
      // If facet is not present, register a listener in case the facet
      // is added later (which happens when creating a new Android project)
      registerFacetListener(project);
      return false;
    }

    return true;
  }

  private static void registerFacetListener(@NotNull Project project) {
    ProjectWideFacetListenersRegistry registry = ProjectWideFacetListenersRegistry.getInstance(project);
    if (registry == null) {
      LOGGER.warn("Cannot find ProjectWideFacetListenersRegistry instance.");
      return;
    }
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    if (toolWindowManager == null) {
      LOGGER.warn("Cannot find ToolWindowManagerEx instance.");
      return;
    }

    registry.registerListener(AndroidFacet.ID, new ProjectWideFacetAdapter<AndroidFacet>() {
      @Override
      public void facetAdded(@NotNull AndroidFacet facet) {
        registry.unregisterListener(AndroidFacet.ID, this);
        if (toolWindowManager.getToolWindow(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID) != null) {
          return;
        }
        Arrays.stream(Extensions.getExtensions(ToolWindowEP.EP_NAME))
          .filter(x -> StringUtil.equals(x.id, DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID))
          .findFirst()
          .ifPresent(toolWindowManager::initToolWindow);
      }
    }, project);
  }

  public static void enableFeature(boolean enabled) {
    myEnabled = enabled;
  }
}
