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

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
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
  val project: Project
  val module: Module
  val fixture: CodeInsightTestFixture
  val disposable: Disposable

  companion object {
    /** Create a [FixtureRule] with an in-memory fake file system. */
    fun create() = inMemory()

    /** Create a [FixtureRule] with an in-memory fake file system. */
    fun inMemory(): FixtureRule {
      val fixtureRule = FixtureRuleWithLightTempDir()
      return createChain(fixtureRule)
    }

    /** Create a [FixtureRule] using a temp directory on disk. */
    fun onDisk(): FixtureRule {
      val fixtureRule = FixtureRuleWithTempDir()
      return createChain(fixtureRule)
    }

    private fun createChain(fixtureRule: FixtureRuleBase): FixtureRule {
      val disposableRule = DisposableRule()
      val chain = RuleChain.outerRule(disposableRule).around(fixtureRule)
      return object : FixtureRule, TestRule by chain {
        override val project: Project
          get() = fixture.project

        override val module: Module
          get() = project.modules.single()

        override val fixture: CodeInsightTestFixture
          get() = fixtureRule.fixture

        override val disposable: Disposable
          get() = disposableRule.disposable
      }
    }
  }
}

private sealed class FixtureRuleBase : ExternalResource() {
  abstract val fixture: CodeInsightTestFixture

  override fun before() {
    fixture.setUp()
  }

  override fun after() {
    fixture.tearDown()
  }
}

/** Fixture that uses a fake file system in memory. */
private open class FixtureRuleWithLightTempDir: FixtureRuleBase() {
  override val fixture by lazy {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(this::class.java.name)
    factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))
  }
}

/** Fixture that uses a temp directory in the real file system. */
private class FixtureRuleWithTempDir: FixtureRuleBase() {
  override val fixture by lazy {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(this::class.java.name)
    factory.createCodeInsightFixture(projectBuilder.fixture, factory.createTempDirTestFixture())
  }

  override fun before() {
    super.before()
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      addContentRootToTempDir(fixture.project.modules.single())
    }
  }

  private fun addContentRootToTempDir(module: Module) {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val nioPath = checkNotNull(fixture.tempDirFixture.tempDirPath.toNioPathOrNull()) { "TempDir path is invalid!" }
    val dir = checkNotNull(nioPath.refreshAndFindVirtualDirectory()) { "Directory $nioPath does not exist!" }
    model.addContentEntry(dir)
    ApplicationManager.getApplication().runWriteAction(model::commit)
    SaveAndSyncHandler.getInstance().scheduleProjectSave(fixture.project)
  }
}
