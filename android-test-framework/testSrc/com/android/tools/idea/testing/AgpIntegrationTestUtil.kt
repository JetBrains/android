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

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter.Companion.configureNewProject
import com.android.tools.idea.gradle.project.importing.withAfterCreate
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestBase

object AgpIntegrationTestUtil {
  /**
   * Imports `project`, syncs the project and checks the result.
   */
  @JvmStatic
  fun importProject(project: Project, jdkVersion: JavaSdkVersion, testRootDisposable: Disposable) {
    val jdkOverride: Sdk? = maybeCreateJdkOverride(jdkVersion)
    if (jdkOverride != null) {
      Disposer.register(testRootDisposable) {
        runWriteActionAndWait {
          ProjectJdkTable.getInstance().removeJdk(jdkOverride)
        }
      }
    }

    fun afterCreate(project: Project) {
      if (jdkOverride != null) {
        runWriteActionAndWait {
          ProjectRootManager.getInstance(project).projectSdk = jdkOverride
        }
      }
    }

    GradleProjectImporter.withAfterCreate(afterCreate = ::afterCreate) {
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
      AndroidTestBase.refreshProjectFiles()
    }
  }

  private fun createEmbeddedJdkInstance(embeddedPath: String): Sdk? {
    return Jdks.getInstance().createJdk(EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot(embeddedPath).toString())
  }

  internal fun maybeCreateJdkOverride(forVersion: JavaSdkVersion): Sdk? {
    val jdkOverride = if (forVersion != IdeSdks.getInstance().jdk?.getJdkVersion()) {
      when (forVersion) {
        JavaSdkVersion.JDK_17 -> createEmbeddedJdkInstance("prebuilts/studio/jdk/jdk17")
        JavaSdkVersion.JDK_11 -> createEmbeddedJdkInstance("prebuilts/studio/jdk/jdk11")
        JavaSdkVersion.JDK_1_8 -> createEmbeddedJdkInstance("prebuilts/studio/jdk")
        else -> error("Unsupported JavaSdkVersion: $forVersion")
      }
    } else null
    return jdkOverride
  }
}