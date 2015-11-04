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

import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceCreator;
import com.android.tools.idea.structure.services.DeveloperServiceCreators;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Panel for Developer Services on-boarding materials (e.g. introductions, tutorials, etc.)
 */
public final class DeveloperServicesSidePanel extends JTabbedPane {

  private ServicesBundleTab myHomeTab;

  private Project myProject;
  private String myActionId;
  private String myBundleName;
  private DeveloperServiceCreators myCreators;

  public DeveloperServicesSidePanel(
      @NotNull Project project, @NotNull String actionId, @Nullable String bundleName) {
    myProject = project;
    myActionId = actionId;
    myBundleName = bundleName;
    myHomeTab = new ServicesBundleTab();

    Module androidModule = null;
    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      if (AndroidFacet.getInstance(m) != null) {
        androidModule = m;
        break;
      }
    }

    for (DeveloperServiceCreators creators : DeveloperServiceCreators.EP_NAME.getExtensions()) {
      if (creators.getDeveloperServiceCreatorsId().equals(myActionId)) {
        myCreators = creators;
        break;
      }
    }

    if (androidModule != null) {
      for (DeveloperServiceCreator creator : myCreators.getCreators()) {
        DeveloperService s = creator.createService(androidModule);
        DeveloperServiceHelperPanel helperPanel = new DeveloperServiceHelperPanel(s);
        myHomeTab.addHelperPanel(helperPanel);
      }
    } else {
      // TODO:  Think of something more clever in this scenario.
      getLog().warn("DeveloperServicesSidePanel will be blank" +
                    " - no Android module is associated with the current project context.");
    }

    // TODO:  Extract name of tab from bundle.xml
    addTab(myBundleName, myHomeTab);
  }

  private static Logger getLog(){
    return Logger.getInstance(DeveloperServicesSidePanel.class);
  }
}
