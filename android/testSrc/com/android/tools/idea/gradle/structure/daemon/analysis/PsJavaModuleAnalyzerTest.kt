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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.intellij.openapi.util.Disposer
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

/**
 * Tests for [PsJavaModuleAnalyzerTest].
 */
class PsJavaModuleAnalyzerTest : DependencyTestCase() {

  fun testPromotionMessages() {
    loadProject(PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()
    try {
      val javaModule = project.findModuleByName("jModuleK") as PsJavaModule
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val analyzer = PsJavaModuleAnalyzer(context)
      val messageCollection = analyzer.analyze(javaModule)

      val messages = messageCollection
        .filter {
          val dependencyName = (it.path as? PsLibraryDependencyNavigationPath)?.toString().orEmpty()
          dependencyName.startsWith("com.example.")
        }
        .map { it.text to it.description!! }
        .toSet()
      // No promoton analysis so far.
      assertThat(messages, equalTo(emptySet()))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
