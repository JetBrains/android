/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.module.StdModuleTypes.JAVA;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

class TopLevelModuleFactory {
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final IdeSdks myIdeSdks;

  TopLevelModuleFactory(@NotNull IdeInfo ideInfo, @NotNull IdeSdks ideSdks) {
    myIdeInfo = ideInfo;
    myIdeSdks = ideSdks;
  }

  void createTopLevelModule(@NotNull Project project) {
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
      if (myIdeInfo.isAndroidStudio()) {
        // If sync fails, make sure that the project has a JDK, otherwise Groovy indices won't work (a common scenario where
        // users will update build.gradle files to fix Gradle sync.)
        // See: https://code.google.com/p/android/issues/detail?id=194621
        Sdk jdk = myIdeSdks.getJdk();
        if (jdk != null) {
          model.setSdk(jdk);
        }
      }
      model.commit();

      FacetManager facetManager = FacetManager.getInstance(module);
      ModifiableFacetModel facetModel = facetManager.createModifiableModel();
      try {
        GradleFacet gradleFacet = GradleFacet.getInstance(module);
        if (gradleFacet == null) {
          // Add "gradle" facet, to avoid balloons about unsupported compilation of modules.
          gradleFacet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
          facetModel.addFacet(gradleFacet);
        }
        gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR;
      }
      finally {
        facetModel.commit();
      }
    }
  }
}
