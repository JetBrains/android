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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import java.nio.file.Paths

/** Tests for [ComposeKDocLinkResolutionService] */
class ComposeKDocLinkResolutionServiceTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = getComposePluginTestDataPath()
    StudioFlags.SAMPLES_SUPPORT_ENABLED.override(true)
  }

  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String =
    "tools/adt/idea/compose-ide-plugin/testData"

  override fun getAdditionalRepos() =
    listOf(
      File(
        getComposePluginTestDataPath(),
        PathUtil.toSystemDependentName(TestProjectPaths.REPO_FOR_SAMPLES_ARTIFACT_TEST),
      )
    )

  /**
   * core:haptics was chosen arbitrarily. Non-samples dependencies were removed. Library is not KMP.
   */
  fun testDownloadingAndAttachingSamples() {
    loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES)

    val libraryFilePaths = LibraryFilePaths.getInstance(myFixture.project)

    val androidxSamples =
      libraryFilePaths
        .getCachedPathsForArtifact("androidx.core.haptics:haptics:1.0.0-alpha01")
        ?.sources!!
    assertThat(androidxSamples.map { it.name })
      .containsExactly(
        "haptics-1.0.0-alpha01-sources.jar",
        "haptics-1.0.0-alpha01-samples-sources.jar",
      )
  }

  fun testResolveSampleReference() {
    loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES)

    val file =
      VfsUtil.findFile(
        Paths.get(project.basePath!!, "/app/src/main/java/com/example/appforsamplestest/Main.kt"),
        false,
      )
    assume().that(file).isNotNull()
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("of|f").navigationElement
    val librarySourceFunction = myFixture.elementAtCaret.navigationElement as KtNamedFunction
    assume().that(librarySourceFunction).isNotNull()

    val sampleTag = librarySourceFunction.docComment!!.getDefaultSection().findTagByName("sample")!!
    val sample =
      PsiTreeUtil.findChildOfType(sampleTag, KDocName::class.java)!!.mainReference.resolve()
    assume().that(sample).isNotNull()
    assertThat(sample!!.kotlinFqName!!.asString())
      .isEqualTo("androidx.core.haptics.samples.PatternWaveform")
  }
}
