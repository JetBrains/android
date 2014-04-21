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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

public abstract class NewProjectImportGradleSyncListener implements GradleSyncListener {
  @Override
  public void syncStarted(@NotNull Project project) {
  }

  @Override
  public void syncFailed(@NotNull final Project project, @NotNull String errorMessage) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        createTopLevelProjectAndOpen(project);
      }
    });
  }

  public static void createTopLevelProjectAndOpen(@NotNull final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        createTopLevelModule(project);
      }
    });

    // Just by opening the project, Studio will show the error message in a balloon notification, automatically.
    Projects.open(project);

    // Activate "Project View" so users don't get an empty window.
    activateProjectView(project);
  }

  private static void createTopLevelModule(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);

    File projectRootDir = new File(project.getBasePath());
    VirtualFile contentRoot = VfsUtil.findFileByIoFile(projectRootDir, true);

    if (contentRoot != null) {
      File moduleFile = new File(projectRootDir, projectRootDir.getName() + ".iml");
      Module module = moduleManager.newModule(moduleFile.getPath(), StdModuleTypes.JAVA.getId());

      // This prevents the balloon "Unsupported Modules detected".
      module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, GradleConstants.SYSTEM_ID.getId());

      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.addContentEntry(contentRoot);
      model.commit();

      FacetManager facetManager = FacetManager.getInstance(module);
      ModifiableFacetModel facetModel = facetManager.createModifiableModel();
      try {
        AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
        if (gradleFacet == null) {
          // Add "gradle" facet, to avoid balloons about unsupported compilation of modules.
          gradleFacet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
          facetModel.addFacet(gradleFacet);
        }
        gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = SdkConstants.GRADLE_PATH_SEPARATOR;

        // Add "android" facet to avoid the balloon "Android Framework detected".
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet == null) {
          androidFacet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
          facetModel.addFacet(androidFacet);
        }

        // This is what actually stops Studio from showing the balloon.
        androidFacet.getProperties().ALLOW_USER_CONFIGURATION = false;
      }
      finally {
        facetModel.commit();
      }
    }
  }

  public static void activateProjectView(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (window != null) {
      window.activate(null, false);
    }
  }
}