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
package com.android.tools.idea.gradle.project.sync.idea.data;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.importing.ProjectFolder.deleteLibrariesFolder;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class IdeaSyncCachesInvalidator extends CachesInvalidator {
  @Override
  public void invalidateCaches() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
        DataNodeCaches.getInstance(project).clearCaches();

        if (IdeInfo.getInstance().isAndroidStudio()) {
          // Remove contents in .idea/libraries to recover from any invalid library entries.
          deleteLibrariesFolder(getBaseDirPath(project));
        }
      }
    }
  }
}
