/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project.create
import com.google.common.collect.Sets
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.Mockito
import org.mockito.Mockito.*
import java.io.File

class TomlIdeGradleVisitorTest : UsefulTestCase() {

  private lateinit var myFixture: JavaCodeInsightTestFixture
  private var tomlVisitor: TomlIdeGradleVisitor = TomlIdeGradleVisitor()
  private lateinit var tomlPath: String
  private lateinit var myModule: Module
  override fun setUp() {
    super.setUp()
    TestUtils.getWorkspaceRoot()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createFixtureBuilder(name)
    val fixture =  JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
    myFixture = fixture
    fixture.setUp()

    // Set up module and content roots
    factory.registerFixtureBuilder(LintIdeTest.LintModuleFixtureBuilder::class.java, LintIdeTest.LintModuleFixtureBuilderImpl::class.java)
    val moduleFixtureBuilder = projectBuilder.addModule(LintIdeTest.LintModuleFixtureBuilder::class.java)
    moduleFixtureBuilder.setModuleRoot(fixture.tempDirPath)
    moduleFixtureBuilder.addContentRoot(fixture.tempDirPath)
    myModule = moduleFixtureBuilder.fixture!!.module
    fixture.allowTreeAccessForAllFiles()

    tomlPath = "gradle/libs.versions.toml"
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testDependencies(){
    val content = "[versions]\n" +
                  "multidexVersion = \"1.0.0\" \n" +
                  "guavaVersion = { prefer = \"11.0.2\" }\n"+
                  "testRunnerVersion = { strictly = \"0.5\" }\n"+
                  "[libraries]\n" +
                  "appcompat1 = 'com.android.support:appcompat-v7:+'\n" +
                  "guava = { module = \"com.google.guava:guava\", version.ref=\"guavaVersion\" }\n" +
                  "appcompat2 = \"com.android.support:appcompat-v7:13.0.0\"\n" +
                  "wearable = { module = \"com.google.android.support:wearable\", version=\"1.2.0\" }\n" +
                  "multidex = { group = \"com.android.support\", name =\"multidex\", version.ref=\"multidexVersion\" } \n" +
                  "testRunner = { module= \"com.android.support.test:runner\", version = { prefer = \"0.3\" } } \n"+
                  "testRunner2 = { module = \"com.android.support.test:runner\", version =  { strictly =\"0.4\" } }\n" +
                  "testRunner3 = { module = \"com.android.support.test:runner\", version.ref =\"testRunnerVersion\" }"

    val tomlFile = myFixture.addFileToProject(tomlPath, content)
    val client = LintIdeClient(myFixture.project, LintEditorResult(myModule, tomlFile.virtualFile, content, Sets.newHashSet()))
    val rootFolder =  File(myFixture.tempDirPath)
    val lintProject = create(client,rootFolder,rootFolder)
    val request = LintIdeRequest(client, myFixture.project, listOf(tomlFile.virtualFile), listOf(myModule), false)
    val driver = LintDriver(BuiltinIssueRegistry(), client, request)

    val mockDetector:GradleDetector = mock(GradleDetector::class.java)
    val gradleContext = GradleContext(tomlVisitor, driver, lintProject, lintProject, VfsUtilCore.virtualToIoFile(tomlFile.virtualFile))
    tomlVisitor.visitBuildScript(gradleContext,listOf(mockDetector))

    val propertyCaptor = ArgumentCaptor.forClass(String::class.java)
    val valueCaptor = ArgumentCaptor.forClass(String::class.java)
    val parentCaptor = ArgumentCaptor.forClass(String::class.java)
    val locationCaptor = ArgumentCaptor.forClass(Any::class.java)

    verify(mockDetector,  times(6)).checkDslPropertyAssignment(MockitoHelper.anyObject(),
                                                    propertyCaptor.capture() ?: "",
                                                    valueCaptor.capture() ?: "",
                                                    parentCaptor.capture() ?: "",
                                                    isNull(),
                                                    locationCaptor.capture() ?: "",
                                                    MockitoHelper.anyObject(),
                                                    MockitoHelper.anyObject())

    assertThat(valueCaptor.allValues).isEqualTo(listOf("'com.android.support:appcompat-v7:+'",
                                                       "'com.android.support:appcompat-v7:13.0.0'",
                                                       "'com.google.android.support:wearable:1.2.0'",
                                                       "'com.android.support:multidex:1.0.0'",
                                                       "'com.android.support.test:runner:0.4!!'",
                                                       "'com.android.support.test:runner:0.5!!'"))
    assertThat(parentCaptor.allValues.toSet()).isEqualTo(setOf("dependencies"))
    assertThat(locationCaptor.allValues.map {
      val psi = (it as Element).psi
      Pair(psi.startOffset, psi.endOffset)
    })
      .isEqualTo(listOf(Pair(141, 177), Pair(265, 306), Pair(318, 385), Pair(29, 36), Pair(585, 663), Pair(95, 115)))
  }

}

// Helper class to transform nullable Java return type to non-nullable Kotlin
object MockitoHelper {
  fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> uninitialized(): T = null as T
}