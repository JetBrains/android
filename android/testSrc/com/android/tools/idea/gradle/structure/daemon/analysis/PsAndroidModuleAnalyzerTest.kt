/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.configurables.PsPathRendererImpl
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.intellij.openapi.util.Disposer
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

/**
 * Tests for [PsAndroidModuleAnalyzer].
 */
class PsAndroidModuleAnalyzerTest : DependencyTestCase() {

  fun testPromotionMessages() {
    loadProject(PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()
    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val mainModule = project.findModuleByName("mainModule") as PsAndroidModule
      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val messageCollection = analyzer.analyze(mainModule)

      val messages = messageCollection
        .filter {
          val dependencyName = (it.path as? PsLibraryDependencyNavigationPath)?.toString().orEmpty()
          dependencyName.startsWith("com.example.")
        }
        .map { it.text to it.description!! }
        .toSet()

      assertThat(messages, equalTo(setOf(
        "Gradle promoted library version from 0.9.1 to 1.0" to "in: releaseImplementation",
        "Gradle promoted library version from 0.6 to 1.0" to "in: freeImplementation",
        "Gradle promoted library version from 0.6 to 1.0" to "in: freeImplementation",
        "Gradle promoted library version from 0.9.1 to 1.0" to "in: releaseImplementation"
      )))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
