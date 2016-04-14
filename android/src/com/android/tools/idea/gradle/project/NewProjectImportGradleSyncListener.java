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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.Projects.open;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.module.StdModuleTypes.JAVA;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW;

public abstract class NewProjectImportGradleSyncListener extends GradleSyncListener.Adapter {
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
    open(project);

    // Activate "Project View" so users don't get an empty window.
    activateProjectView(project);
  }

  @VisibleForTesting
  public static void createTopLevelModule(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);

    File projectRootDir = getBaseDirPath(project);
    VirtualFile contentRoot = findFileByIoFile(projectRootDir, true);

    if (contentRoot != null) {
      File moduleFile = new File(projectRootDir, projectRootDir.getName() + ".iml");
      Module module = moduleManager.newModule(moduleFile.getPath(), JAVA.getId());

      // This prevents the balloon "Unsupported Modules detected".
      module.setOption(EXTERNAL_SYSTEM_ID_KEY, GRADLE_SYSTEM_ID.getId());

      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.addContentEntry(contentRoot);
      if (isAndroidStudio()) {
        // If sync fails, make sure that the project has a JDK, otherwise Groovy indices won't work (a common scenario where
        // users will update build.gradle files to fix Gradle sync.)
        // See: https://code.google.com/p/android/issues/detail?id=194621
        Sdk jdk = IdeSdks.getJdk();
        if (jdk != null) {
          model.setSdk(jdk);
        }
      }
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
        gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR;

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
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(PROJECT_VIEW);
    if (window != null) {
      window.activate(null, false);
    }
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
  }
}