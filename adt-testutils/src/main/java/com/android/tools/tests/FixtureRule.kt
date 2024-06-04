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
package com.android.tools.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * Rule that sets up a basic fixture for adding/opening files, etc. but without anything
 * Android-related.
 */
interface FixtureRule : TestRule {
  val projectIfOpened: ProjectEx?
  val project: ProjectEx
  val module: Module
  val fixture: CodeInsightTestFixture
  val disposable: Disposable

  companion object {
    /** Create a [FixtureRule]. */
    fun create(): FixtureRule {
      val projectRule = ProjectRule()
      val fixtureRule = FixtureRuleImpl(projectRule::project)
      val chain = RuleChain.outerRule(projectRule).around(fixtureRule)
      return object : FixtureRule, TestRule by chain {
        override val projectIfOpened: ProjectEx?
          get() = projectRule.projectIfOpened

        override val project: ProjectEx
          get() = projectRule.project

        override val module: Module
          get() = projectRule.module

        override val fixture: CodeInsightTestFixture
          get() = fixtureRule.fixture

        override val disposable: Disposable
          get() = project.earlyDisposable
      }
    }
  }
}

private class FixtureRuleImpl(
  /**
   * This must be a supplier because the project might not be ready when this object is constructed.
   */
  private val projectSupplier: () -> Project
) : ExternalResource() {
  val fixture by lazy {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(projectSupplier().name)
    factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))
  }

  override fun before() {
    fixture.setUp()
  }

  override fun after() {
    fixture.tearDown()
  }
}
