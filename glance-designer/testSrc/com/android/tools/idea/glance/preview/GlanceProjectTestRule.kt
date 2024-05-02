/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.testing.TestLoggerRule
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** [TestRule] that implements the [before] and [after] setup specific for Glance unit tests. */
private class GlanceProjectRuleImpl(private val projectRule: AndroidProjectRule) :
  NamedExternalResource() {
  override fun before(description: Description) {
    (projectRule.module.getModuleSystem() as? DefaultModuleSystem)?.let { it.usesCompose = true }
    projectRule.fixture.stubComposableAnnotation()
    projectRule.fixture.stubGlancePreviewAnnotation()
  }

  override fun after(description: Description) {}
}

/**
 * A [TestRule] providing the same behaviour as [AndroidProjectRule] but with the correct setup for
 * testing Glance preview elements.
 */
class GlanceProjectRule(
  private val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()
) : TestRule {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val delegate =
    RuleChain.outerRule(TestLoggerRule())
      .around(projectRule)
      .around(GlanceProjectRuleImpl(projectRule))

  override fun apply(base: Statement, description: Description): Statement =
    delegate.apply(base, description)
}

fun CodeInsightTestFixture.stubGlancePreviewAnnotation(modulePath: String = "") {
  addFileToProject(
    "$modulePath/src/androidx/glance/preview/Preview.kt",
    // language=kotlin
    """
    package androidx.glance.preview

    @Repeatable
    annotation class Preview(
      val widthDp: Int = -1,
      val heightDp: Int = -1,
    )
    """
      .trimIndent(),
  )
}
