/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test

class GradleSyncSpecialCasesIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `manifest in build folder`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APPAND_LIB)
    val libProjectDir = preparedProject.root.resolve("lib")
    val oldManifestFile = libProjectDir.resolve("src/main/AndroidManifest.xml")
    val newManifestFile = libProjectDir.resolve("build/generated/manifests/AndroidManifest.xml")
    FileUtil.createParentDirs(newManifestFile)
    oldManifestFile.renameTo(newManifestFile)
    libProjectDir.resolve("build.gradle").appendText("""
     android.sourceSets.main.manifest.srcFile '${newManifestFile.absolutePath}'
    """)
    preparedProject.open { project ->
      val manifestVirtualFile = VfsUtil.findFile(newManifestFile.toPath(), false)
      expect.that(manifestVirtualFile).isNotNull()
      expect.that(manifestVirtualFile?.let { runReadAction {  AndroidFacet.getInstance(it, project) } }).isNotNull()
    }
  }
}
