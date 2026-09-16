/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.declarative.runsGradle

import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.disableKtsIndexing
import com.android.tools.idea.testing.withDeclarative
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeclarativeInternalSchemaTest {
  @get:Rule val projectRule = AndroidGradleProjectRule().withDeclarative()
  private val project
    get() = projectRule.project

  @Before
  fun before() {
    DeclarativeIdeSupport.override(true)
  }

  @After fun onAfter() = DeclarativeIdeSupport.clearOverride()

  @Test
  fun testSchemaForInternalProject() {
    disableKtsIndexing(project, projectRule.fixture.testRootDisposable)
    projectRule.loadProject(TestProjectPaths.DECLARATIVE_ANDROID_INCLUDED_BUILD)

    val root = projectRule.project.basePath!!
    val service = DeclarativeService.Companion.getInstance(project)
    runReadAction {
      // Verify included build schema is retrieved correctly
      val includedSettingsFile = LocalFileSystem.getInstance().findFileByIoFile(File(root, "build-logic/settings.gradle.dcl"))
      Truth.assertThat(includedSettingsFile).isNotNull()
      val includedContext = PsiManager.getInstance(project).findFile(includedSettingsFile!!)
      Truth.assertThat(includedContext).isNotNull()
      val includedSchema = service.getDeclarativeSchema(includedContext!!)
      Truth.assertThat(includedSchema).isNotNull()
      Truth.assertThat(includedSchema?.projects).hasSize(1)
      Truth.assertThat(includedSchema?.projects?.first()?.topLevelReceiver?.memberFunctions?.find { it.name == "javaGradlePlugin" })
        .isNotNull()

      // Verify root build schema is retrieved correctly
      val rootSettingsFile = LocalFileSystem.getInstance().findFileByIoFile(File(root, "settings.gradle.dcl"))
      Truth.assertThat(rootSettingsFile).isNotNull()
      val rootContext = PsiManager.getInstance(project).findFile(rootSettingsFile!!)
      val rootSchema = service.getDeclarativeSchema(rootContext!!)
      Truth.assertThat(rootSchema).isNotNull()
      Truth.assertThat(rootSchema?.projects).hasSize(1)
      Truth.assertThat(rootSchema?.projects?.first()?.topLevelReceiver?.memberFunctions?.find { it.name == "androidApp" }).isNotNull()
    }
  }
}
