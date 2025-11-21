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
package com.android.tools.compose

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.getLibraryAdditionalArtifactPaths
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import com.intellij.util.PathUtil.toSystemDependentName
import java.io.File
import java.nio.file.Paths
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Rule
import org.junit.Test

/** Tests for [ComposeKDocLinkResolutionService] */
@RunsInEdt
class ComposeKDocLinkResolutionServiceTest {
  @get:Rule
  val projectRule =
    AndroidGradleProjectRule(
        workspaceRelativeTestDataPath = "tools/adt/idea/compose-ide-plugin/testData",
        additionalRepositories =
          listOf(
            File(
              getComposePluginTestDataPath(),
              toSystemDependentName("projects/repoForSamplesArtifactTest"),
            )
          ),
      )
      .onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy {
    projectRule.fixture.also { it.testDataPath = getComposePluginTestDataPath() }
  }

  /**
   * core:haptics was chosen arbitrarily. Non-samples dependencies were removed. Library is not KMP.
   */
  @Test
  fun testDownloadingAndAttachingSamples() {
    projectRule.loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES)

    val result = getLibraryAdditionalArtifactPaths(project, LibraryPathType.SOURCE)

    assertThat(result.filter { it.contains("haptics") }.map { File(it).name })
      .containsExactly(
        "haptics-1.0.0-alpha01-sources.jar",
        "haptics-1.0.0-alpha01-samples-sources.jar",
      )
  }

  @Test
  fun testResolveSampleReference() {
    projectRule.loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES)

    val file =
      VfsUtil.findFile(
        Paths.get(project.basePath!!, "/app/src/main/java/com/example/appforsamplestest/Main.kt"),
        false,
      )
    assume().that(file).isNotNull()
    fixture.openFileInEditor(file!!)

    fixture.moveCaret("of|f").navigationElement
    val librarySourceFunction = fixture.elementAtCaret.navigationElement as KtNamedFunction
    assume().that(librarySourceFunction).isNotNull()

    val sampleTag = librarySourceFunction.docComment!!.getDefaultSection().findTagByName("sample")!!
    val sample =
      PsiTreeUtil.findChildOfType(sampleTag, KDocName::class.java)!!.mainReference.resolve()
    assume().that(sample).isNotNull()
    assertThat(sample!!.kotlinFqName!!.asString())
      .isEqualTo("androidx.core.haptics.samples.PatternWaveform")
  }
}
