/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.devservices;

import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMap;
import com.android.tools.idea.structure.services.DeveloperServices;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.List;

/**
 * Triggers the creation of the Developer Services side panel.
 */
public class OpenDeveloperServicesSidePanelAction extends AnAction {

  private static final String TOOL_WINDOW_TITLE = "Assistant";

  @Override
  public final void actionPerformed(AnActionEvent event) {
    final Project thisProject = event.getProject();
    final String actionId = ActionManager.getInstance().getId(this);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ModuleManager moduleManager = ModuleManager.getInstance(thisProject);
        Module[] modules = moduleManager.getModules();

        List<Module> moduleList = Lists.newArrayList();
        if (Projects.isBuildWithGradle(thisProject)) {
          for (Module module : modules) {
            // Filter to Android modules
            if (AndroidFacet.getInstance(module) != null) {
              moduleList.add(module);
            }
          }
        }

        // TODO: Subscribe to changes on the editor and notify the assist panel when the focused file's project or module changes. Send down
        // the new service map when this occurs. The plugin will be responsible for updating state (buttons generally) as necessary.
        // subscription.
         DeveloperServiceMap serviceMap = new DeveloperServiceMap();

        for (Module module : moduleList) {
          for (DeveloperService service : DeveloperServices.getAll(module)) {
            serviceMap.put(service.getMetadata().getId(), service);
          }
        }

        DeveloperServicesToolWindowFactory factory = new DeveloperServicesToolWindowFactory(actionId, serviceMap);
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(thisProject);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_TITLE);

        if (toolWindow == null) {
          toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_TITLE, true, ToolWindowAnchor.RIGHT);
        }

        factory.createToolWindowContent(thisProject, toolWindow);

        // Always active the window, in case it was previously minimized.
        toolWindow.activate(null);
      }
    });
  }
}
