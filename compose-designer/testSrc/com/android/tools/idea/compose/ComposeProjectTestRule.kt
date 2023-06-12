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
package com.android.tools.idea.compose

import com.android.tools.idea.compose.preview.PreviewEntryPoint
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.registerExtension
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** [TestRule] that implements the [before] and [after] setup specific for Compose unit tests. */
private class ComposeProjectRuleImpl(
  private val projectRule: AndroidProjectRule,
  private val previewAnnotationPackage: String,
  private val composableAnnotationPackage: String
) : NamedExternalResource() {
  override fun before(description: Description) {
    // Kotlin UnusedSymbolInspection caches the extensions during the initialization so,
    // unfortunately we have to do this to ensure
    // our entry point detector is registered early enough
    ApplicationManager.getApplication()
      .registerExtension(
        ExtensionPointName<PreviewEntryPoint>("com.intellij.deadCode"),
        PreviewEntryPoint(),
        projectRule.fixture.testRootDisposable
      )

    (projectRule.module.getModuleSystem() as? DefaultModuleSystem)?.let { it.usesCompose = true }
    projectRule.fixture.stubComposableAnnotation(composableAnnotationPackage)
    projectRule.fixture.stubPreviewAnnotation(previewAnnotationPackage)
  }

  override fun after(description: Description) {}
}

/**
 * A [TestRule] providing the same behaviour as [AndroidProjectRule] but with the correct setup for
 * testing Compose preview elements.
 */
class ComposeProjectRule(
  private val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory(),
  previewAnnotationPackage: String,
  composableAnnotationPackage: String
) : TestRule {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val delegate =
    RuleChain.outerRule(projectRule)
      .around(
        ComposeProjectRuleImpl(projectRule, previewAnnotationPackage, composableAnnotationPackage)
      )

  override fun apply(base: Statement, description: Description): Statement =
    delegate.apply(base, description)
}
