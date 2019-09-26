/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.ui.resourcemanager.explorer.NoFacetView
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Test

class ResourceExplorerToolWindowTest {

  lateinit var fixture: CodeInsightTestFixture
  lateinit var module: Module
  var facet: AndroidFacet? = null

  val project: Project get() = fixture.project

  @Before
  fun before() {
    fixture = createLightFixture()
    fixture.setUp()
    module = fixture.module
  }

  @After
  fun after() {
    if (facet != null) {
      runInEdtAndWait {
        val facetManager = FacetManager.getInstance(module)
        val facetModel = facetManager.createModifiableModel()
        facetModel.removeFacet(facet)
        ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      }
    }
    fixture.tearDown()
  }

  private fun initFacet() {
    val facetManager = FacetManager.getInstance(module)
    facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null)
    runInEdtAndWait {
      val facetModel = facetManager.createModifiableModel()
      facetModel.addFacet(facet)
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
    }
  }

  private fun createLightFixture(): CodeInsightTestFixture {
    // This is a very abstract way to initialize a new Project and a single Module.
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(LightJavaCodeInsightFixtureTestCase.JAVA_8)
    return factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))
  }

  @Test
  fun createWithoutAndroidFacet() {
    val windowManager = ToolWindowManagerEx.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(
      NoFacetView::class.java)
  }

  @Test
  fun createWithAndroidFacet() {
    val windowManager = ToolWindowManagerEx.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(ResourceExplorer::class.java)
  }
}