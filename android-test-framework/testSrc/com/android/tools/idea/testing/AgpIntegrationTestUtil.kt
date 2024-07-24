/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter.Companion.configureNewProject
import com.android.tools.idea.gradle.project.importing.withAfterCreate
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.JdkUtils.overrideProjectGradleJdkPathWithVersion
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestBase

object AgpIntegrationTestUtil {
  /**
   * Imports `project`, syncs the project and checks the result.
   */
  @JvmStatic
  fun importProject(project: Project, jdkVersion: JavaSdkVersion) {
    GradleProjectImporter.withAfterCreate(
      afterCreate = { overrideProjectGradleJdkPathWithVersion(Projects.getBaseDirPath(project), jdkVersion) }
    ) {
      runInEdtAndWait {
        val request = GradleProjectImporter.Request(project)
        configureNewProject(project)
        GradleProjectImporter.getInstance().importProjectNoSync(request)
        AndroidGradleTests.syncProject(
          project,
          GradleSyncInvoker.Request.testRequest()
        ) { it: TestGradleSyncListener ->
          AndroidGradleTests.checkSyncStatus(
            project,
            it
          )
        }
      }
      IndexingTestUtil.waitUntilIndexesAreReady(project);
      runInEdtAndWait {
        AndroidGradleTests.waitForCreateRunConfigurations(project)
      }
      AndroidTestBase.refreshProjectFiles()
    }
  }
}
