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

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.android.tools.idea.testing.TestLoggerRule
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** [TestRule] that implements the [before] and [after] setup specific for Compose unit tests. */
private class ComposeProjectRuleImpl(private val projectRule: AndroidProjectRule) :
  NamedExternalResource() {
  val buildSystemServices = FakeBuildSystemFilePreviewServices()

  override fun before(description: Description) {
    buildSystemServices.register(projectRule.testRootDisposable)
    (projectRule.module.getModuleSystem() as? DefaultModuleSystem)?.let { it.usesCompose = true }
    projectRule.fixture.stubComposableAnnotation()
    projectRule.fixture.stubPreviewAnnotation()
  }

  override fun after(description: Description) {}
}

/**
 * A [TestRule] providing the same behaviour as [AndroidProjectRule] but with the correct setup for
 * testing Compose preview elements.
 */
class ComposeProjectRule(
  private val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()
) : TestRule {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val implRule = ComposeProjectRuleImpl(projectRule)

  val buildSystemServices: FakeBuildSystemFilePreviewServices
    get() = implRule.buildSystemServices

  val delegate = RuleChain.outerRule(TestLoggerRule()).around(projectRule).around(implRule)

  override fun apply(base: Statement, description: Description): Statement =
    delegate.apply(base, description)
}
